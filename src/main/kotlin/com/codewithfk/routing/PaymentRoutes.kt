package com.codewithfk.routing

import com.codewithfk.dto.*
import com.codewithfk.dto.ErrorResponse
import com.codewithfk.dto.PaymentIntentStatusResponse
import com.codewithfk.services.BookingService
import com.codewithfk.services.PaymentService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.paymentRoutes(paymentService: PaymentService, bookingService: BookingService) {
    route("/payments") {
        authenticate("auth-jwt") {
            post("/intent") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                println("[PAYMENT] POST /payments/intent — userId=$userId")

                if (userId == null) {
                    println("[PAYMENT] Intent rejected: missing userId in token")
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                try {
                    val request = call.receive<CreatePaymentIntentRequest>()
                    println("[PAYMENT] Intent request: bookingId=${request.bookingId}, amount=${request.amount}, currency=${request.currency}")

                    val booking = bookingService.getBookingById(request.bookingId)
                        ?: throw IllegalArgumentException("Booking not found")
                    println("[PAYMENT] Booking found: id=${booking.id}, customerId=${booking.customerId}, totalPrice=${booking.totalPrice}, status=${booking.status}, paymentStatus=${booking.paymentStatus}")

                    if (booking.customerId != userId) {
                        println("[PAYMENT] Intent rejected: booking belongs to ${booking.customerId}, not $userId")
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Booking does not belong to user"))
                        return@post
                    }

                    val amount = request.amount?.let { it * 100 }
                        ?: (booking.totalPrice * 100).toLong()
                    println("[PAYMENT] Calculated amount: ${amount} cents (${amount / 100.0} ${request.currency})")

                    val paymentIntent = paymentService.createPaymentIntent(
                        bookingId = request.bookingId,
                        amount = amount,
                        currency = request.currency,
                        customerId = null
                    )
                    println("[PAYMENT] Intent created: paymentIntentId=${paymentIntent.paymentIntentId}, status=${paymentIntent.status}, amount=${paymentIntent.amount} ${paymentIntent.currency}")

                    call.respond(paymentIntent)
                } catch (e: Exception) {
                    println("[PAYMENT] Intent failed: ${e.javaClass.simpleName} — ${e.message}")
                    e.printStackTrace()
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Failed to create payment intent"))
                }
            }

            post("/refund") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                val role = principal?.payload?.getClaim("role")?.asString()

                println("[PAYMENT] POST /payments/refund — userId=$userId, role=$role")

                if (userId == null) {
                    println("[PAYMENT] Refund rejected: missing userId in token")
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                try {
                    val request = call.receive<RefundRequest>()
                    println("[PAYMENT] Refund request: bookingId=${request.bookingId}, amount=${request.amount}, reason=${request.reason}")

                    val booking = bookingService.getBookingById(request.bookingId)
                        ?: throw IllegalArgumentException("Booking not found")
                    println("[PAYMENT] Booking found: id=${booking.id}, customerId=${booking.customerId}, paymentId=${booking.paymentId}, paymentStatus=${booking.paymentStatus}")

                    if (booking.customerId != userId && role != "ADMIN") {
                        println("[PAYMENT] Refund rejected: user $userId (role=$role) not authorized for booking owned by ${booking.customerId}")
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Not authorized to refund this booking"))
                        return@post
                    }

                    val amountCents = request.amount?.let { it * 100 }
                    println("[PAYMENT] Refund amount: ${amountCents?.let { "$it cents" } ?: "full refund"}")

                    val refund = paymentService.processRefund(
                        bookingId = request.bookingId,
                        amount = amountCents,
                        reason = request.reason
                    )
                    println("[PAYMENT] Refund processed: refundId=${refund.refundId}, amount=${refund.amount} ${refund.currency}, status=${refund.status}")

                    call.respond(refund)
                } catch (e: Exception) {
                    println("[PAYMENT] Refund failed: ${e.javaClass.simpleName} — ${e.message}")
                    e.printStackTrace()
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Failed to process refund"))
                }
            }

            get("/intent/{paymentIntentId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                val paymentIntentId = call.parameters["paymentIntentId"] ?: run {
                    println("[PAYMENT] GET /payments/intent — missing paymentIntentId param")
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }

                println("[PAYMENT] GET /payments/intent/$paymentIntentId — userId=$userId")

                if (userId == null) {
                    println("[PAYMENT] Intent lookup rejected: missing userId in token")
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }

                val paymentIntent = paymentService.getPaymentIntent(paymentIntentId)
                if (paymentIntent == null) {
                    println("[PAYMENT] Intent not found: $paymentIntentId")
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                println("[PAYMENT] Intent retrieved: id=${paymentIntent.id}, status=${paymentIntent.status}, amount=${paymentIntent.amount} ${paymentIntent.currency}")

                val bookingId = paymentIntent.metadata["bookingId"]
                if (bookingId != null) {
                    val booking = bookingService.getBookingById(bookingId)
                    if (booking != null && booking.customerId != userId) {
                        println("[PAYMENT] Intent lookup rejected: booking $bookingId belongs to ${booking.customerId}, not $userId")
                        call.respond(HttpStatusCode.Forbidden)
                        return@get
                    }
                }

                call.respond(
                    PaymentIntentStatusResponse(
                        id = paymentIntent.id,
                        status = paymentIntent.status,
                        amount = paymentIntent.amount,
                        currency = paymentIntent.currency
                    )
                )
            }
        }
    }
}

