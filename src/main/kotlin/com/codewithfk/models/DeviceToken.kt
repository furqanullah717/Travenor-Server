package com.codewithfk.models

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import kotlinx.datetime.Clock

object DeviceTokens : UUIDTable("device_tokens") {
    val userId = uuid("user_id").references(Users.id)
    val token = varchar("token", 512)
    val platform = varchar("platform", 10) // ANDROID, IOS
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

enum class DevicePlatform {
    ANDROID,
    IOS
}
