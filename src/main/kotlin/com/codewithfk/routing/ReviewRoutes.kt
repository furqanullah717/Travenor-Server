package com.codewithfk.routing

import com.codewithfk.dto.*
import com.codewithfk.dto.ErrorResponse
import com.codewithfk.services.ReviewService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.reviewRoutes(reviewService: ReviewService) {
    route("/reviews") {
        // Public endpoints
        get("/listing/{listingId}") {
            val listingId = call.parameters["listingId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
            
            val reviews = reviewService.getReviewsByListing(listingId, page, pageSize)
            call.respond(reviews)
        }
        
        // Authenticated endpoints
        authenticate("auth-jwt") {
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                
                try {
                    val request = call.receive<CreateReviewRequest>()
                    val review = reviewService.createReview(
                        customerId = userId,
                        listingId = request.listingId,
                        bookingId = request.bookingId,
                        rating = request.rating,
                        title = request.title,
                        comment = request.comment
                    )
                    
                    call.respond(HttpStatusCode.Created, review)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Bad request"))
                }
            }
            
            get("/my-reviews") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
                
                val reviews = reviewService.getReviewsByCustomer(userId, page, pageSize)
                call.respond(reviews)
            }
            
            get("/{id}") {
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                
                val review = reviewService.getReviewById(id)
                if (review == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                
                call.respond(review)
            }
            
            put("/{id}") {
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
                
                val review = reviewService.getReviewById(id)
                if (review == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@put
                }
                
                // Check if user owns the review
                if (review.customerId != userId) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@put
                }
                
                try {
                    val request = call.receive<Map<String, String?>>()
                    val rating = request["rating"]?.toIntOrNull()
                    val title = request["title"]
                    val comment = request["comment"]
                    
                    val updatedReview = reviewService.updateReview(id, rating, title, comment)
                    if (updatedReview == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@put
                    }
                    
                    call.respond(updatedReview)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Bad request"))
                }
            }
            
            delete("/{id}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                val role = principal?.payload?.getClaim("role")?.asString()
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@delete
                }
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@delete
                }
                
                val review = reviewService.getReviewById(id)
                if (review == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@delete
                }
                
                // Check if user owns the review or is admin
                if (review.customerId != userId && role != "ADMIN") {
                    call.respond(HttpStatusCode.Forbidden)
                    return@delete
                }
                
                val deleted = reviewService.deleteReview(id)
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
            
            // Admin only
            put("/{id}/approve") {
                val principal = call.principal<JWTPrincipal>()
                val role = principal?.payload?.getClaim("role")?.asString()
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@put
                }
                
                if (role != "ADMIN") {
                    call.respond(HttpStatusCode.Forbidden)
                    return@put
                }
                
                val approvedReview = reviewService.approveReview(id)
                if (approvedReview == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@put
                }
                
                call.respond(approvedReview)
            }
        }
    }
}

