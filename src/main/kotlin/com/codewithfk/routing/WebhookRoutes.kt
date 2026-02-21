package com.codewithfk.routing

import com.codewithfk.dto.ErrorResponse
import com.codewithfk.dto.WebhookResponse
import com.codewithfk.services.BookingService
import com.codewithfk.services.NotificationService
import com.codewithfk.services.PaymentService
import com.stripe.model.Event
import com.stripe.model.PaymentIntent
import com.stripe.net.Webhook
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

fun Route.webhookRoutes(paymentService: PaymentService, bookingService: BookingService, notificationService: NotificationService) {
    route("/webhooks") {
        post("/stripe") {
            println("[WEBHOOK] POST /webhooks/stripe — received incoming Stripe event")
            try {
                val payload = call.receiveText()
                println("[WEBHOOK] Payload size: ${payload.length} bytes")

                val sigHeader = call.request.header("Stripe-Signature")
                    ?: throw IllegalArgumentException("Missing Stripe-Signature header")
                println("[WEBHOOK] Stripe-Signature header present")

                val webhookSecret = paymentService.getWebhookSecret()
                val event: Event

                try {
                    event = Webhook.constructEvent(payload, sigHeader, webhookSecret)
                } catch (e: Exception) {
                    println("[WEBHOOK] Signature verification failed: ${e.message}")
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid signature"))
                    return@post
                }

                println("[WEBHOOK] Event verified: type=${event.type}, id=${event.id}")

                val deserializer = event.dataObjectDeserializer
                val stripeObject = if (deserializer.getObject().isPresent) {
                    deserializer.getObject().get()
                } else {
                    println("[WEBHOOK] API version mismatch (event=${event.apiVersion}), using deserializeUnsafe()")
                    deserializer.deserializeUnsafe()
                }

                when (event.type) {
                    "payment_intent.succeeded" -> {
                        val paymentIntent = stripeObject as PaymentIntent
                        val bookingId = paymentIntent.metadata["bookingId"]
                        println("[WEBHOOK] payment_intent.succeeded: paymentIntentId=${paymentIntent.id}, bookingId=$bookingId, amount=${paymentIntent.amount} ${paymentIntent.currency}")

                        if (bookingId != null) {
                            bookingService.updatePaymentStatus(bookingId, "PAID", paymentIntent.id)
                            println("[WEBHOOK] Booking $bookingId payment status updated to PAID")
                            val confirmedBooking = bookingService.updateBookingStatus(bookingId, "CONFIRMED")
                            println("[WEBHOOK] Booking $bookingId status updated to CONFIRMED")
                            if (confirmedBooking != null) {
                                notificationService.sendBookingConfirmation(confirmedBooking)
                                println("[WEBHOOK] Booking confirmation notification sent for $bookingId")
                            }
                        } else {
                            println("[WEBHOOK] No bookingId in metadata, skipping booking update")
                        }
                    }
                    "payment_intent.payment_failed" -> {
                        val paymentIntent = stripeObject as PaymentIntent
                        val bookingId = paymentIntent.metadata["bookingId"]
                        val failureMessage = paymentIntent.lastPaymentError?.message ?: "unknown"
                        println("[WEBHOOK] payment_intent.payment_failed: paymentIntentId=${paymentIntent.id}, bookingId=$bookingId, reason=$failureMessage")
                    }
                    "charge.refunded" -> {
                        println("[WEBHOOK] charge.refunded: ${stripeObject.javaClass.simpleName}, id=${event.id}")
                    }
                    else -> {
                        println("[WEBHOOK] Unhandled event type: ${event.type}")
                    }
                }

                println("[WEBHOOK] Event ${event.id} processed successfully")
                call.respond(HttpStatusCode.OK, WebhookResponse(received = true))
            } catch (e: Exception) {
                println("[WEBHOOK] Processing failed: ${e.javaClass.simpleName} — ${e.message}")
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Webhook processing failed"))
            }
        }
    }
}

