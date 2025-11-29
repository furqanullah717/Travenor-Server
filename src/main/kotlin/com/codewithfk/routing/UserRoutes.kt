package com.codewithfk.routing

import com.codewithfk.dto.UserResponse
import com.codewithfk.dto.ErrorResponse
import com.codewithfk.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoutes(userService: UserService) {
    route("/users") {
        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                
                val user = userService.getUserById(userId)
                if (user == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                
                call.respond(user)
            }
            
            put("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@put
                }
                
                try {
                    val request = call.receive<Map<String, String?>>()
                    val firstName = request["firstName"]
                    val lastName = request["lastName"]
                    val phone = request["phone"]
                    
                    val updatedUser = userService.updateUser(userId, firstName, lastName, phone)
                    if (updatedUser == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@put
                    }
                    
                    call.respond(updatedUser)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Bad request"))
                }
            }
            
            get("/{id}") {
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                
                val user = userService.getUserById(id)
                if (user == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                
                call.respond(user)
            }
            
            // Admin only
            get {
                val principal = call.principal<JWTPrincipal>()
                val role = principal?.payload?.getClaim("role")?.asString()
                
                if (role != "ADMIN") {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
                
                val users = userService.getAllUsers(page, pageSize)
                call.respond(users)
            }
        }
    }
}

