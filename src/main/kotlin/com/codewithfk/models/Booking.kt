package com.codewithfk.models

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.math.BigDecimal
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ReferenceOption
import java.util.UUID

object Bookings : UUIDTable("bookings") {
    val customerId = uuid("customer_id").references(Users.id)
    val listingId = uuid("listing_id").references(TravelListings.id)
    val tripDateId = uuid("trip_date_id").references(TripDates.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val checkInDate = timestamp("check_in_date").nullable() // For flexible date bookings (backward compatibility)
    val checkOutDate = timestamp("check_out_date").nullable() // For flexible date bookings (backward compatibility)
    val numberOfGuests = integer("number_of_guests").default(1)
    val totalPrice = decimal("total_price", 10, 2)
    val currency = varchar("currency", 3).default("USD")
    val status = varchar("status", 20).default("PENDING") // PENDING, CONFIRMED, CANCELLED, COMPLETED
    val paymentStatus = varchar("payment_status", 20).default("PENDING") // PENDING, PAID, REFUNDED
    val paymentId = varchar("payment_id", 255).nullable()
    val specialRequests = text("special_requests").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

enum class BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED
}

enum class PaymentStatus {
    PENDING,
    PAID,
    REFUNDED
}

