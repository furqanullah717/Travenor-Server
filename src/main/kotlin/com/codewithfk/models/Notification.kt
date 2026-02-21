package com.codewithfk.models

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import kotlinx.datetime.Clock

object Notifications : UUIDTable("notifications") {
    val userId = uuid("user_id").references(Users.id)
    val title = varchar("title", 255)
    val body = text("body")
    val type = varchar("type", 30)
    val referenceId = uuid("reference_id").nullable()
    val isRead = bool("is_read").default(false)
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

enum class NotificationType {
    BOOKING_CONFIRMED,
    TRIP_REMINDER,
    RATE_TRIP,
    BOOKING_CANCELLED
}
