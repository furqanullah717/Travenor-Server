package com.codewithfk

import com.codewithfk.database.DatabaseFactory
import com.codewithfk.plugins.*
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val config = environment.config
    
    // Initialize database
    DatabaseFactory.init(config)
    
    // Initialize services
    val imageService = com.codewithfk.services.ImageService().apply { init(config) }
    
    // Configure plugins
    configureSerialization()
    configureAuthentication(config)
    configureCORS()
    configureStatusPages()
    configureSwagger()
    
    // Configure routing
    configureRouting(imageService)
}

