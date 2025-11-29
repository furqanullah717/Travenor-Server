package com.codewithfk.models

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.util.UUID

object Reviews : UUIDTable("reviews") {
    val customerId = uuid("customer_id").references(Users.id)
    val listingId = uuid("listing_id").references(TravelListings.id)
    val bookingId = uuid("booking_id").references(Bookings.id).nullable()
    val rating = integer("rating") // 1-5
    val title = varchar("title", 255).nullable()
    val comment = text("comment").nullable()
    val isVerified = bool("is_verified").default(false) // Verified purchase
    val isApproved = bool("is_approved").default(true)
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

