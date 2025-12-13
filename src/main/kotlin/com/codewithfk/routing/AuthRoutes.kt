package com.codewithfk.routing

import com.codewithfk.dto.*
import com.codewithfk.dto.ErrorResponse
import com.codewithfk.security.JwtConfig
import com.codewithfk.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(userService: UserService) {
    route("/auth") {
        post("/register") {
            try {
                val request = call.receive<RegisterRequest>()
                
                // Validate email format
                if (!request.email.matches(Regex("^[A-Za-z0-9+_.-]+@(.+)$"))) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid email format"))
                    return@post
                }
                
                // Check if user exists
                val existingUser = userService.getUserByEmail(request.email)
                if (existingUser != null) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse("User with this email already exists"))
                    return@post
                }
                
                // Validate password
                if (request.password.length < 6) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Password must be at least 6 characters"))
                    return@post
                }
                
                val user = userService.createUser(
                    email = request.email,
                    password = request.password,
                    firstName = request.firstName,
                    lastName = request.lastName,
                    phone = request.phone,
                    role = request.role
                )
                
                val token = JwtConfig.generateToken(user)
                call.respond(HttpStatusCode.Created, AuthResponse(token = token, user = user))
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Registration failed"))
            }
        }
        
        post("/login") {
            try {
                val request = call.receive<LoginRequest>()
                
                val user = userService.getUserByEmail(request.email)
                if (user == null || !user.isActive) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid credentials"))
                    return@post
                }
                
                val isValid = userService.verifyPassword(request.email, request.password)
                if (!isValid) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid credentials"))
                    return@post
                }
                
                val token = JwtConfig.generateToken(user)
                call.respond(AuthResponse(token = token, user = user))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Login failed"))
            }
        }
        
        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                    return@get
                }
                
                val user = userService.getUserById(userId)
                if (user == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                    return@get
                }
                
                call.respond(user)
            }
        }
    }
}

