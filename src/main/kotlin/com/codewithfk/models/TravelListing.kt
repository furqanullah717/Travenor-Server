package com.codewithfk.models

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.math.BigDecimal
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.util.UUID

object TravelListings : UUIDTable("travel_listings") {
    val vendorId = uuid("vendor_id").references(Users.id)
    val title = varchar("title", 255)
    val description = text("description")
    val category = varchar("category", 50) // HOTEL, FLIGHT, ACTIVITY, PACKAGE
    val location = varchar("location", 255)
    val city = varchar("city", 100).nullable()
    val country = varchar("country", 100).nullable()
    val price = decimal("price", 10, 2)
    val currency = varchar("currency", 3).default("USD")
    val capacity = integer("capacity").nullable() // For hotels/activities
    val availableFrom = timestamp("available_from").nullable()
    val availableTo = timestamp("available_to").nullable()
    val images = text("images").nullable() // JSON array of image URLs
    val amenities = text("amenities").nullable() // JSON array
    val rating = decimal("rating", 3, 2).default(BigDecimal.ZERO)
    val reviewCount = integer("review_count").default(0)
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

enum class ListingCategory {
    HOTEL,
    FLIGHT,
    ACTIVITY,
    PACKAGE
}

