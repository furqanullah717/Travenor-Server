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
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                
                try {
                    val request = call.receive<CreatePaymentIntentRequest>()
                    val booking = bookingService.getBookingById(request.bookingId)
                        ?: throw IllegalArgumentException("Booking not found")
                    
                    // Verify booking belongs to user
                    if (booking.customerId != userId) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Booking does not belong to user"))
                        return@post
                    }
                    
                    // Calculate amount in cents
                    val amount = request.amount?.let { it * 100 } 
                        ?: (booking.totalPrice * 100).toLong()
                    
                    val paymentIntent = paymentService.createPaymentIntent(
                        bookingId = request.bookingId,
                        amount = amount,
                        currency = request.currency,
                        customerId = null
                    )
                    
                    call.respond(paymentIntent)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Failed to create payment intent"))
                }
            }
            
            post("/refund") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                val role = principal?.payload?.getClaim("role")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                
                // Only ADMIN or booking owner can refund
                try {
                    val request = call.receive<RefundRequest>()
                    val booking = bookingService.getBookingById(request.bookingId)
                        ?: throw IllegalArgumentException("Booking not found")
                    
                    if (booking.customerId != userId && role != "ADMIN") {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Not authorized to refund this booking"))
                        return@post
                    }
                    
                    val refund = paymentService.processRefund(
                        bookingId = request.bookingId,
                        amount = request.amount?.let { it * 100 }, // Convert to cents
                        reason = request.reason
                    )
                    
                    call.respond(refund)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Failed to process refund"))
                }
            }
            
            get("/intent/{paymentIntentId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                val paymentIntentId = call.parameters["paymentIntentId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                
                val paymentIntent = paymentService.getPaymentIntent(paymentIntentId)
                if (paymentIntent == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                
                // Verify booking belongs to user
                val bookingId = paymentIntent.metadata["bookingId"]
                if (bookingId != null) {
                    val booking = bookingService.getBookingById(bookingId)
                    if (booking != null && booking.customerId != userId) {
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

