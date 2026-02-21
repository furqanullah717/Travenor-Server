package com.codewithfk.routing

import com.codewithfk.dto.*
import com.codewithfk.services.NotificationService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.notificationRoutes(notificationService: NotificationService) {
    route("/notifications") {
        authenticate("auth-jwt") {
            post("/device-token") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val request = call.receive<RegisterDeviceTokenRequest>()
                notificationService.registerDeviceToken(userId, request.token, request.platform)
                call.respond(HttpStatusCode.OK, mapOf("status" to "registered"))
            }

            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }

                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

                val notifications = notificationService.getNotifications(userId, page, pageSize)
                val unreadCount = notificationService.getUnreadCount(userId)

                call.respond(
                    NotificationListResponse(
                        notifications = notifications,
                        unreadCount = unreadCount,
                        page = page,
                        pageSize = pageSize
                    )
                )
            }

            get("/unread-count") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }

                val count = notificationService.getUnreadCount(userId)
                call.respond(UnreadCountResponse(count = count))
            }

            put("/{id}/read") {
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

                val updated = notificationService.markAsRead(id, userId)
                if (updated) {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "read"))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            put("/read-all") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@put
                }

                notificationService.markAllAsRead(userId)
                call.respond(HttpStatusCode.OK, mapOf("status" to "all_read"))
            }
        }
    }
}
