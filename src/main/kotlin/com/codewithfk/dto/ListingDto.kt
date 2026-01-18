package com.codewithfk.dto

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

@Serializable
data class CreateListingRequest(
    val title: String,
    val description: String,
    val category: String,
    val location: String,
    val city: String? = null,
    val country: String? = null,
    val price: Double,
    val currency: String = "USD",
    val capacity: Int? = null,
    val availableFrom: String? = null,
    val availableTo: String? = null,
    val images: List<String>? = null,
    val amenities: List<String>? = null
)

@Serializable
data class UpdateListingRequest(
    val title: String? = null,
    val description: String? = null,
    val location: String? = null,
    val city: String? = null,
    val country: String? = null,
    val price: Double? = null,
    val currency: String? = null,
    val capacity: Int? = null,
    val availableFrom: String? = null,
    val availableTo: String? = null,
    val images: List<String>? = null,
    val amenities: List<String>? = null,
    val isActive: Boolean? = null
)

@Serializable
data class ListingResponse(
    val id: String,
    val vendorId: String,
    val title: String,
    val description: String,
    val category: String,
    val location: String,
    val city: String?,
    val country: String?,
    val price: Double,
    val currency: String,
    val capacity: Int?,
    val availableFrom: String?,
    val availableTo: String?,
    val images: List<String>?,
    val amenities: List<String>?,
    val tripDates: List<TripDateResponse>? = null, // Predefined dates for this listing
    val rating: Double,
    val reviewCount: Int,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class ListingSearchRequest(
    val category: String? = null,
    val location: String? = null,
    val city: String? = null,
    val country: String? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val minRating: Double? = null,
    val page: Int = 1,
    val pageSize: Int = 20
)

