package com.codewithfk.routing

import com.codewithfk.dto.*
import com.codewithfk.dto.ErrorResponse
import com.codewithfk.dto.AvailabilityCheckResponse
import com.codewithfk.dto.PriceCalculationResponse
import com.codewithfk.services.BookingService
import com.codewithfk.services.BookingAvailabilityService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.bookingRoutes(
    bookingService: BookingService,
    availabilityService: BookingAvailabilityService
) {
    route("/bookings") {
        authenticate("auth-jwt") {
            // Check availability and calculate price
            post("/check-availability") {
                try {
                    val request = call.receive<CreateBookingRequest>()
                    val checkIn = request.checkInDate?.let { kotlinx.datetime.Instant.parse(it) }
                    val checkOut = request.checkOutDate?.let { kotlinx.datetime.Instant.parse(it) }
                    
                    val availability = availabilityService.checkAvailability(
                        listingId = request.listingId,
                        checkInDate = checkIn,
                        checkOutDate = checkOut,
                        numberOfGuests = request.numberOfGuests
                    )
                    
                    val priceCalculation = if (availability.available) {
                        val calc = availabilityService.calculatePrice(
                            listingId = request.listingId,
                            checkInDate = checkIn,
                            checkOutDate = checkOut,
                            numberOfGuests = request.numberOfGuests
                        )
                        PriceCalculationResponse(
                            basePrice = calc.basePrice,
                            tax = calc.tax,
                            serviceFee = calc.serviceFee,
                            total = calc.total,
                            currency = calc.currency,
                            nights = calc.nights,
                            numberOfGuests = calc.numberOfGuests
                        )
                    } else null
                    
                    call.respond(
                        AvailabilityCheckResponse(
                            available = availability.available,
                            reason = availability.reason,
                            priceCalculation = priceCalculation
                        )
                    )
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Bad request"))
                }
            }
            
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                
                try {
                    val request = call.receive<CreateBookingRequest>()
                    
                    // Check availability first
                    val checkIn = request.checkInDate?.let { kotlinx.datetime.Instant.parse(it) }
                    val checkOut = request.checkOutDate?.let { kotlinx.datetime.Instant.parse(it) }
                    
                    val availability = availabilityService.checkAvailability(
                        listingId = request.listingId,
                        checkInDate = checkIn,
                        checkOutDate = checkOut,
                        numberOfGuests = request.numberOfGuests
                    )
                    
                    if (!availability.available) {
                        call.respond(HttpStatusCode.BadRequest, mapOf(
                            "error" to "Booking not available",
                            "reason" to availability.reason
                        ))
                        return@post
                    }
                    
                    // Calculate price
                    val priceCalculation = availabilityService.calculatePrice(
                        listingId = request.listingId,
                        checkInDate = checkIn,
                        checkOutDate = checkOut,
                        numberOfGuests = request.numberOfGuests
                    )
                    
                    val booking = bookingService.createBooking(
                        customerId = userId,
                        listingId = request.listingId,
                        checkInDate = request.checkInDate,
                        checkOutDate = request.checkOutDate,
                        numberOfGuests = request.numberOfGuests,
                        specialRequests = request.specialRequests,
                        totalPrice = java.math.BigDecimal.valueOf(priceCalculation.total)
                    )
                    
                    call.respond(HttpStatusCode.Created, booking)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Bad request"))
                }
            }
            
            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                val role = principal?.payload?.getClaim("role")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
                
                val bookings = bookingService.getBookingsByCustomer(userId, page, pageSize)
                call.respond(bookings)
            }
            
            get("/{id}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                val role = principal?.payload?.getClaim("role")?.asString()
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                
                val booking = bookingService.getBookingById(id)
                if (booking == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                
                // Check if user owns the booking or is admin/vendor
                if (booking.customerId != userId && role != "ADMIN" && role != "VENDOR") {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                
                call.respond(booking)
            }
            
            put("/{id}/status") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                val role = principal?.payload?.getClaim("role")?.asString()
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@put
                }
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@put
                }
                
                val booking = bookingService.getBookingById(id)
                if (booking == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@put
                }
                
                // Only admin or vendor can update booking status
                if (role != "ADMIN" && role != "VENDOR") {
                    call.respond(HttpStatusCode.Forbidden)
                    return@put
                }
                
                try {
                    val request = call.receive<UpdateBookingStatusRequest>()
                    val updatedBooking = bookingService.updateBookingStatus(id, request.status)
                    
                    if (updatedBooking == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@put
                    }
                    
                    call.respond(updatedBooking)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Bad request"))
                }
            }
            
            put("/{id}/payment") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@put
                }
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@put
                }
                
                try {
                    val request = call.receive<Map<String, String>>()
                    val paymentStatus = request["paymentStatus"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("paymentStatus is required"))
                        return@put
                    }
                    val paymentId = request["paymentId"]
                    
                    val updatedBooking = bookingService.updatePaymentStatus(id, paymentStatus, paymentId)
                    
                    if (updatedBooking == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@put
                    }
                    
                    call.respond(updatedBooking)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Bad request"))
                }
            }
            
            delete("/{id}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@delete
                }
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@delete
                }
                
                val cancelledBooking = bookingService.cancelBooking(id, userId)
                if (cancelledBooking == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@delete
                }
                
                call.respond(cancelledBooking)
            }
        }
    }
}

