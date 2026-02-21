package com.codewithfk.services

import com.codewithfk.database.DatabaseFactory
import com.codewithfk.dto.BookingResponse
import com.codewithfk.dto.NotificationResponse
import com.codewithfk.models.*
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import io.ktor.server.config.*
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.io.FileInputStream
import java.util.UUID

class NotificationService {
    private var firebaseInitialized = false

    fun init(config: ApplicationConfig) {
        val serviceAccountPath = config.propertyOrNull("firebase.serviceAccountPath")?.getString()
        if (serviceAccountPath == null) {
            println("[NotificationService] No firebase.serviceAccountPath configured — push disabled")
            return
        }

        val file = java.io.File(serviceAccountPath)
        val resolvedFile = if (file.exists()) {
            file
        } else {
            println("[NotificationService] Not found at '${file.absolutePath}', checking classpath…")
            val classPathStream = this::class.java.classLoader.getResourceAsStream(serviceAccountPath.removePrefix("./"))
            if (classPathStream != null) {
                val tempFile = java.io.File.createTempFile("firebase-sa-", ".json").apply { deleteOnExit() }
                tempFile.outputStream().use { classPathStream.copyTo(it) }
                tempFile
            } else null
        }

        if (resolvedFile != null && resolvedFile.exists()) {
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(FileInputStream(resolvedFile)))
                .build()
            FirebaseApp.initializeApp(options)
            firebaseInitialized = true
            println("[NotificationService] Firebase initialized from ${resolvedFile.absolutePath}")
        } else {
            println("[NotificationService] Firebase service account file not found — tried: ${file.absolutePath}")
            println("[NotificationService] Push notifications disabled. Set FIREBASE_SERVICE_ACCOUNT_PATH to the absolute path of your service account JSON.")
        }
    }

    // --- Device token management ---

    suspend fun registerDeviceToken(userId: String, token: String, platform: String) {
        DatabaseFactory.dbQuery {
            val uid = UUID.fromString(userId)
            DeviceTokens.update({
                (DeviceTokens.userId eq uid) and (DeviceTokens.platform eq platform)
            }) {
                it[isActive] = false
                it[updatedAt] = Clock.System.now()
            }
            DeviceTokens.insert {
                it[DeviceTokens.userId] = uid
                it[DeviceTokens.token] = token
                it[DeviceTokens.platform] = platform.uppercase()
                it[isActive] = true
                it[createdAt] = Clock.System.now()
                it[updatedAt] = Clock.System.now()
            }
        }
    }

    suspend fun deactivateToken(token: String) {
        DatabaseFactory.dbQuery {
            DeviceTokens.update({ DeviceTokens.token eq token }) {
                it[isActive] = false
                it[updatedAt] = Clock.System.now()
            }
        }
    }

    suspend fun getActiveTokensForUser(userId: String): List<String> = DatabaseFactory.dbQuery {
        DeviceTokens.select {
            (DeviceTokens.userId eq UUID.fromString(userId)) and (DeviceTokens.isActive eq true)
        }.map { it[DeviceTokens.token] }
    }

    // --- Push notification sending + persistence ---

    suspend fun sendNotification(
        userId: String,
        title: String,
        body: String,
        type: NotificationType,
        referenceId: String? = null,
        data: Map<String, String> = emptyMap()
    ) {
        DatabaseFactory.dbQuery {
            Notifications.insert {
                it[Notifications.userId] = UUID.fromString(userId)
                it[Notifications.title] = title
                it[Notifications.body] = body
                it[Notifications.type] = type.name
                it[Notifications.referenceId] = referenceId?.let { id -> UUID.fromString(id) }
                it[isRead] = false
                it[createdAt] = Clock.System.now()
            }
        }

        if (!firebaseInitialized) return

        val tokens = getActiveTokensForUser(userId)
        for (fcmToken in tokens) {
            val messageBuilder = Message.builder()
                .setToken(fcmToken)
                .putData("type", type.name)
                .putData("title", title)
                .putData("body", body)
            referenceId?.let { messageBuilder.putData("referenceId", it) }
            data.forEach { (k, v) -> messageBuilder.putData(k, v) }
            messageBuilder.setNotification(
                com.google.firebase.messaging.Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            try {
                FirebaseMessaging.getInstance().send(messageBuilder.build())
            } catch (e: FirebaseMessagingException) {
                if (e.messagingErrorCode == MessagingErrorCode.UNREGISTERED) {
                    deactivateToken(fcmToken)
                }
                println("[NotificationService] FCM send failed for token ${fcmToken.take(12)}…: ${e.message}")
            }
        }
    }

    suspend fun sendBookingConfirmation(booking: BookingResponse) {
        sendNotification(
            userId = booking.customerId,
            title = "Booking Confirmed",
            body = "Your booking #${booking.id.take(8)} has been confirmed.",
            type = NotificationType.BOOKING_CONFIRMED,
            referenceId = booking.id
        )
    }

    suspend fun sendTripReminder(booking: BookingResponse) {
        sendNotification(
            userId = booking.customerId,
            title = "Trip Reminder",
            body = "Your trip is starting soon! Booking #${booking.id.take(8)}.",
            type = NotificationType.TRIP_REMINDER,
            referenceId = booking.id
        )
    }

    suspend fun sendRatingPrompt(booking: BookingResponse) {
        sendNotification(
            userId = booking.customerId,
            title = "Rate Your Trip",
            body = "How was your experience? Leave a review for booking #${booking.id.take(8)}.",
            type = NotificationType.RATE_TRIP,
            referenceId = booking.id,
            data = mapOf("action" to "rate", "bookingId" to booking.id)
        )
    }

    // --- Notification history ---

    suspend fun getNotifications(userId: String, page: Int, pageSize: Int): List<NotificationResponse> =
        DatabaseFactory.dbQuery {
            Notifications.select { Notifications.userId eq UUID.fromString(userId) }
                .orderBy(Notifications.createdAt, SortOrder.DESC)
                .limit(pageSize, offset = ((page - 1) * pageSize).toLong())
                .map { rowToNotification(it) }
        }

    suspend fun getUnreadCount(userId: String): Int = DatabaseFactory.dbQuery {
        Notifications.select {
            (Notifications.userId eq UUID.fromString(userId)) and (Notifications.isRead eq false)
        }.count().toInt()
    }

    suspend fun markAsRead(notificationId: String, userId: String): Boolean = DatabaseFactory.dbQuery {
        Notifications.update({
            (Notifications.id eq UUID.fromString(notificationId)) and
                    (Notifications.userId eq UUID.fromString(userId))
        }) {
            it[isRead] = true
        } > 0
    }

    suspend fun markAllAsRead(userId: String) {
        DatabaseFactory.dbQuery {
            Notifications.update({
                (Notifications.userId eq UUID.fromString(userId)) and (Notifications.isRead eq false)
            }) {
                it[isRead] = true
            }
        }
    }

    private fun rowToNotification(row: ResultRow): NotificationResponse = NotificationResponse(
        id = row[Notifications.id].toString(),
        title = row[Notifications.title],
        body = row[Notifications.body],
        type = row[Notifications.type],
        referenceId = row[Notifications.referenceId]?.toString(),
        isRead = row[Notifications.isRead],
        createdAt = row[Notifications.createdAt].toString()
    )
}
