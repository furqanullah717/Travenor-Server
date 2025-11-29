package com.codewithfk.routing

import com.codewithfk.dto.ErrorResponse
import com.codewithfk.dto.WebhookResponse
import com.codewithfk.services.BookingService
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

fun Route.webhookRoutes(paymentService: PaymentService, bookingService: BookingService) {
    route("/webhooks") {
        post("/stripe") {
            try {
                val payload = call.receiveText()
                val sigHeader = call.request.header("Stripe-Signature")
                    ?: throw IllegalArgumentException("Missing Stripe-Signature header")
                
                val webhookSecret = paymentService.getWebhookSecret()
                val event: Event
                
                try {
                    event = Webhook.constructEvent(payload, sigHeader, webhookSecret)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid signature"))
                    return@post
                }
                
                // Handle the event
                when (event.type) {
                    "payment_intent.succeeded" -> {
                        val paymentIntent = event.dataObjectDeserializer.getObject() as PaymentIntent
                        val bookingId = paymentIntent.metadata["bookingId"]
                        
                        if (bookingId != null) {
                            // Update booking status
                            bookingService.updatePaymentStatus(bookingId, "PAID", paymentIntent.id)
                            // Auto-confirm booking if payment succeeds
                            bookingService.updateBookingStatus(bookingId, "CONFIRMED")
                        }
                    }
                    "payment_intent.payment_failed" -> {
                        val paymentIntent = event.dataObjectDeserializer.getObject() as PaymentIntent
                        val bookingId = paymentIntent.metadata["bookingId"]
                        
                        if (bookingId != null) {
                            // Optionally cancel booking on payment failure
                            // bookingService.updateBookingStatus(bookingId, "CANCELLED")
                        }
                    }
                    "charge.refunded" -> {
                        // Handle refund webhook
                        val charge = event.dataObjectDeserializer.getObject()
                        // Update booking payment status to REFUNDED
                    }
                }
                
                call.respond(HttpStatusCode.OK, WebhookResponse(received = true))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Webhook processing failed"))
            }
        }
    }
}

