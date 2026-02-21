package com.codewithfk

import com.codewithfk.database.DatabaseFactory
import com.codewithfk.plugins.*
import com.codewithfk.scheduling.BookingScheduler
import com.codewithfk.services.BookingService
import com.codewithfk.services.NotificationService
import io.ktor.server.application.*
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val config = environment.config
    
    // Initialize database
    DatabaseFactory.init(config)
    
    // Initialize services
    val imageService = com.codewithfk.services.ImageService().apply { init(config) }
    val notificationService = NotificationService().apply { init(config) }
    
    // Configure plugins
    configureSerialization()
    configureAuthentication(config)
    configureCORS()
    configureStatusPages()
    configureSwagger()
    
    // Configure routing
    configureRouting(imageService, notificationService)
    
    // Start booking scheduler
    val bookingScheduler = BookingScheduler(BookingService(), notificationService)
    launch { bookingScheduler.start(this) }
}

