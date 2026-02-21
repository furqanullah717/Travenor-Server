package com.codewithfk.scheduling

import com.codewithfk.database.DatabaseFactory
import com.codewithfk.dto.BookingResponse
import com.codewithfk.models.*
import com.codewithfk.services.BookingService
import com.codewithfk.services.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import kotlin.time.Duration.Companion.hours

class BookingScheduler(
    private val bookingService: BookingService,
    private val notificationService: NotificationService
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                runCatching { autoCompleteBookings() }
                    .onFailure { println("[BookingScheduler] autoComplete error: ${it.message}") }
                runCatching { sendTripReminders() }
                    .onFailure { println("[BookingScheduler] tripReminders error: ${it.message}") }
                delay(1.hours)
            }
        }
    }

    private suspend fun autoCompleteBookings() {
        val now = Clock.System.now()
        val bookingsToComplete = DatabaseFactory.dbQuery {
            Bookings.select {
                (Bookings.status eq BookingStatus.CONFIRMED.name) and
                        (Bookings.checkOutDate.isNotNull() and (Bookings.checkOutDate less now))
            }.map { it[Bookings.id].value.toString() }
        }

        val tripDateBookings = DatabaseFactory.dbQuery {
            (Bookings innerJoin TripDates).select {
                (Bookings.status eq BookingStatus.CONFIRMED.name) and
                        (Bookings.tripDateId.isNotNull()) and
                        (TripDates.endDate less now)
            }.map { it[Bookings.id].value.toString() }
        }

        val allIds = (bookingsToComplete + tripDateBookings).distinct()

        for (id in allIds) {
            val updated = bookingService.updateBookingStatus(id, BookingStatus.COMPLETED.name)
            if (updated != null) {
                notificationService.sendRatingPrompt(updated)
                println("[BookingScheduler] Auto-completed booking $id")
            }
        }
    }

    private suspend fun sendTripReminders() {
        val now = Clock.System.now()
        val in24h = now.plus(24.hours)

        val upcomingBookings = DatabaseFactory.dbQuery {
            (Bookings innerJoin TripDates).select {
                (Bookings.status eq BookingStatus.CONFIRMED.name) and
                        (Bookings.tripDateId.isNotNull()) and
                        (TripDates.startDate greater now) and
                        (TripDates.startDate less in24h)
            }.map { it[Bookings.id].value.toString() to it[Bookings.customerId].toString() }
        }

        for ((bookingId, _) in upcomingBookings) {
            val alreadySent = DatabaseFactory.dbQuery {
                Notifications.select {
                    (Notifications.referenceId eq java.util.UUID.fromString(bookingId)) and
                            (Notifications.type eq NotificationType.TRIP_REMINDER.name)
                }.count() > 0
            }
            if (alreadySent) continue

            val booking = bookingService.getBookingById(bookingId) ?: continue
            notificationService.sendTripReminder(booking)
            println("[BookingScheduler] Sent trip reminder for booking $bookingId")
        }
    }
}
