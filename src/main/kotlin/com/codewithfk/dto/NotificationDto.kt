package com.codewithfk.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceTokenRequest(
    val token: String,
    val platform: String
)

@Serializable
data class NotificationResponse(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val referenceId: String?,
    val isRead: Boolean,
    val createdAt: String
)

@Serializable
data class NotificationListResponse(
    val notifications: List<NotificationResponse>,
    val unreadCount: Int,
    val page: Int,
    val pageSize: Int
)

@Serializable
data class UnreadCountResponse(
    val count: Int
)
