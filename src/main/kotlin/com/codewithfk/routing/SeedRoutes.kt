package com.codewithfk.routing

import com.codewithfk.dto.SeedResponse
import com.codewithfk.dto.SeedInfoResponse
import com.codewithfk.dto.TestUserInfo
import com.codewithfk.dto.ErrorResponse
import com.codewithfk.services.SeedDataService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.seedRoutes() {
    route("/seed") {
        post {
            try {
                val seedService = SeedDataService()
                seedService.seedDatabase()
                call.respond(
                    HttpStatusCode.OK,
                    SeedResponse(
                        message = "Database seeded successfully",
                        testUsers = mapOf(
                            "admin" to TestUserInfo("admin@trevnor.com", "password123", "ADMIN"),
                            "vendor1" to TestUserInfo("vendor1@trevnor.com", "password123", "VENDOR"),
                            "vendor2" to TestUserInfo("vendor2@trevnor.com", "password123", "VENDOR"),
                            "customer1" to TestUserInfo("customer1@trevnor.com", "password123", "CUSTOMER"),
                            "customer2" to TestUserInfo("customer2@trevnor.com", "password123", "CUSTOMER")
                        ),
                        note = "Use these credentials to test the API. All users have password: password123"
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to seed database")
                )
            }
        }
        
        // Get seed data info (what was seeded)
        get {
            call.respond(
                HttpStatusCode.OK,
                SeedInfoResponse(
                    message = "Seed endpoint - POST to /seed to populate database",
                    testUsers = mapOf(
                        "admin" to TestUserInfo("admin@trevnor.com", "password123"),
                        "vendor1" to TestUserInfo("vendor1@trevnor.com", "password123"),
                        "vendor2" to TestUserInfo("vendor2@trevnor.com", "password123"),
                        "customer1" to TestUserInfo("customer1@trevnor.com", "password123"),
                        "customer2" to TestUserInfo("customer2@trevnor.com", "password123")
                    ),
                    sampleListings = listOf(
                        "Luxury Beach Resort - Maldives (HOTEL)",
                        "Mountain View Hotel - Swiss Alps (HOTEL)",
                        "Round Trip to Paris (FLIGHT)",
                        "Safari Adventure - Serengeti (ACTIVITY)",
                        "Tokyo City Tour (ACTIVITY)",
                        "Complete Bali Experience Package (PACKAGE)"
                    )
                )
            )
        }
    }
}
