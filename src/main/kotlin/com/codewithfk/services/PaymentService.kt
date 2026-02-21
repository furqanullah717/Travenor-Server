package com.codewithfk.services

import com.codewithfk.database.DatabaseFactory
import com.codewithfk.models.Bookings
import com.codewithfk.models.PaymentStatus
import com.stripe.Stripe
import com.stripe.exception.StripeException
import com.stripe.model.PaymentIntent
import com.stripe.model.Refund
import com.stripe.param.PaymentIntentCreateParams
import com.stripe.param.RefundCreateParams
import io.ktor.server.config.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

class PaymentService {
    private var stripeSecretKey: String = ""
    private var webhookSecret: String = ""
    
    fun init(config: ApplicationConfig) {
        stripeSecretKey = config.propertyOrNull("stripe.secretKey")?.getString() 
            ?: ""
        webhookSecret = config.propertyOrNull("stripe.webhookSecret")?.getString() ?: ""
        
        // Ensure no whitespace or quote issues
        stripeSecretKey = stripeSecretKey.trim().removeSurrounding("\"")
        
        println("Stripe API Key initialized: ${stripeSecretKey.take(20)}...")
        Stripe.apiKey = stripeSecretKey
    }
    
    suspend fun createPaymentIntent(
        bookingId: String,
        amount: Long,
        currency: String = "usd",
        customerId: String? = null
    ): PaymentIntentResponse = DatabaseFactory.dbQuery {
        println("[PAYMENT-SVC] createPaymentIntent: bookingId=$bookingId, amount=$amount cents, currency=$currency, stripeCustomerId=$customerId")

        val booking = Bookings.select { Bookings.id eq UUID.fromString(bookingId) }.singleOrNull()
            ?: throw IllegalArgumentException("Booking not found")
        println("[PAYMENT-SVC] Booking DB row: customerId=${booking[Bookings.customerId]}, paymentStatus=${booking[Bookings.paymentStatus]}, currentPaymentId=${booking[Bookings.paymentId]}")

        try {
            val paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency(currency.lowercase())
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build()
                )

            paramsBuilder.putMetadata("bookingId", bookingId)
            paramsBuilder.putMetadata("customerId", booking[Bookings.customerId].toString())

            customerId?.let { paramsBuilder.setCustomer(it) }

            val params = paramsBuilder.build()
            println("[PAYMENT-SVC] Calling Stripe PaymentIntent.create — amount=$amount, currency=${currency.lowercase()}")

            val paymentIntent = PaymentIntent.create(params)
            println("[PAYMENT-SVC] Stripe response: id=${paymentIntent.id}, status=${paymentIntent.status}, amount=${paymentIntent.amount} ${paymentIntent.currency}")

            Bookings.update({ Bookings.id eq UUID.fromString(bookingId) }) {
                it[Bookings.paymentId] = paymentIntent.id
            }
            println("[PAYMENT-SVC] Booking $bookingId updated with paymentId=${paymentIntent.id}")

            PaymentIntentResponse(
                clientSecret = paymentIntent.clientSecret,
                paymentIntentId = paymentIntent.id,
                amount = paymentIntent.amount,
                currency = paymentIntent.currency,
                status = paymentIntent.status
            )
        } catch (e: StripeException) {
            println("[PAYMENT-SVC] Stripe error: code=${e.code}, statusCode=${e.statusCode}, message=${e.message}")
            println("[PAYMENT-SVC] Stripe API key in use: ${Stripe.apiKey?.take(20)}...")
            throw PaymentException("Failed to create payment intent: ${e.message}", e)
        }
    }
    
    suspend fun confirmPayment(paymentIntentId: String): PaymentIntent? {
        println("[PAYMENT-SVC] confirmPayment: paymentIntentId=$paymentIntentId")
        return try {
            val paymentIntent = PaymentIntent.retrieve(paymentIntentId)
            println("[PAYMENT-SVC] Retrieved intent: status=${paymentIntent.status}, amount=${paymentIntent.amount} ${paymentIntent.currency}")

            if (paymentIntent.status == "succeeded") {
                val bookingId = paymentIntent.metadata["bookingId"]
                println("[PAYMENT-SVC] Payment succeeded — bookingId=$bookingId, updating payment status to PAID")
                if (bookingId != null) {
                    DatabaseFactory.dbQuery {
                        Bookings.update({ Bookings.paymentId eq paymentIntentId }) {
                            it[Bookings.paymentStatus] = PaymentStatus.PAID.name
                        }
                    }
                    println("[PAYMENT-SVC] Booking payment status updated to PAID via paymentId=$paymentIntentId")
                }
            } else {
                println("[PAYMENT-SVC] Payment not yet succeeded, current status: ${paymentIntent.status}")
            }

            paymentIntent
        } catch (e: StripeException) {
            println("[PAYMENT-SVC] confirmPayment failed: code=${e.code}, message=${e.message}")
            throw PaymentException("Failed to confirm payment: ${e.message}", e)
        }
    }
    
    suspend fun processRefund(
        bookingId: String,
        amount: Long? = null,
        reason: String? = null
    ): RefundResponse = DatabaseFactory.dbQuery {
        println("[PAYMENT-SVC] processRefund: bookingId=$bookingId, amount=${amount?.let { "$it cents" } ?: "full"}, reason=$reason")

        val booking = Bookings.select { Bookings.id eq UUID.fromString(bookingId) }.singleOrNull()
            ?: throw IllegalArgumentException("Booking not found")

        val paymentId = booking[Bookings.paymentId]
            ?: throw IllegalArgumentException("No payment found for this booking")
        println("[PAYMENT-SVC] Booking DB row: paymentId=$paymentId, paymentStatus=${booking[Bookings.paymentStatus]}")

        if (booking[Bookings.paymentStatus] != PaymentStatus.PAID.name) {
            println("[PAYMENT-SVC] Refund rejected: booking payment status is ${booking[Bookings.paymentStatus]}, not PAID")
            throw IllegalArgumentException("Booking is not paid, cannot refund")
        }

        try {
            val params = RefundCreateParams.builder()
                .setPaymentIntent(paymentId)
                .apply {
                    amount?.let { setAmount(it) }
                    reason?.let { setReason(RefundCreateParams.Reason.valueOf(it.uppercase())) }
                }
                .build()
            println("[PAYMENT-SVC] Calling Stripe Refund.create — paymentIntent=$paymentId")

            val refund = Refund.create(params)
            println("[PAYMENT-SVC] Stripe refund response: id=${refund.id}, amount=${refund.amount} ${refund.currency}, status=${refund.status}")

            Bookings.update({ Bookings.id eq UUID.fromString(bookingId) }) {
                it[Bookings.paymentStatus] = PaymentStatus.REFUNDED.name
            }
            println("[PAYMENT-SVC] Booking $bookingId payment status updated to REFUNDED")

            RefundResponse(
                refundId = refund.id,
                amount = refund.amount,
                currency = refund.currency,
                status = refund.status,
                reason = refund.reason
            )
        } catch (e: StripeException) {
            println("[PAYMENT-SVC] Stripe refund error: code=${e.code}, statusCode=${e.statusCode}, message=${e.message}")
            throw PaymentException("Failed to process refund: ${e.message}", e)
        }
    }
    
    suspend fun getPaymentIntent(paymentIntentId: String): PaymentIntent? {
        println("[PAYMENT-SVC] getPaymentIntent: $paymentIntentId")
        return try {
            val intent = PaymentIntent.retrieve(paymentIntentId)
            println("[PAYMENT-SVC] Retrieved: id=${intent.id}, status=${intent.status}, amount=${intent.amount} ${intent.currency}")
            intent
        } catch (e: StripeException) {
            println("[PAYMENT-SVC] getPaymentIntent failed: code=${e.code}, message=${e.message}")
            null
        }
    }
    
    fun getWebhookSecret(): String = webhookSecret
}
@Serializable
data class PaymentIntentResponse(
    val clientSecret: String,
    val paymentIntentId: String,
    val amount: Long,
    val currency: String,
    val status: String
)

data class RefundResponse(
    val refundId: String,
    val amount: Long,
    val currency: String,
    val status: String,
    val reason: String?
)

class PaymentException(message: String, cause: Throwable? = null) : Exception(message, cause)

