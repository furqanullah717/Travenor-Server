package com.codewithfk

import com.codewithfk.routing.*
import com.codewithfk.services.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    imageService: com.codewithfk.services.ImageService,
    notificationService: NotificationService
) {
    val config = environment.config
    
    // Initialize services
    val userService = UserService()
    val listingService = ListingService()
    val bookingService = BookingService()
    val reviewService = ReviewService()
    val paymentService = PaymentService().apply { init(config) }
    val availabilityService = BookingAvailabilityService()
    
    routing {
        // Health check
        get("/") {
            call.respond(mapOf("status" to "ok", "service" to "Trevnor Travel Marketplace API"))
        }
        
        // Route modules
        authRoutes(userService)
        userRoutes(userService)
        listingRoutes(listingService)
        bookingRoutes(bookingService, availabilityService, notificationService)
        reviewRoutes(reviewService)
        paymentRoutes(paymentService, bookingService)
        webhookRoutes(paymentService, bookingService, notificationService)
        imageRoutes(imageService, listingService)
        notificationRoutes(notificationService)
        seedRoutes()
    }
}

