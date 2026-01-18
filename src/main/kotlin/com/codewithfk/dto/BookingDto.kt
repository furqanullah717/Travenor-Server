package com.codewithfk.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateBookingRequest(
    val listingId: String,
    val tripDateId: String? = null, // For predefined date trips
    val checkInDate: String? = null, // For flexible date trips (backward compatibility)
    val checkOutDate: String? = null, // For flexible date trips (backward compatibility)
    val numberOfGuests: Int = 1,
    val specialRequests: String? = null
)

@Serializable
data class BookingResponse(
    val id: String,
    val customerId: String,
    val listingId: String,
    val tripDateId: String?,
    val checkInDate: String?,
    val checkOutDate: String?,
    val numberOfGuests: Int,
    val totalPrice: Double,
    val currency: String,
    val status: String,
    val paymentStatus: String,
    val paymentId: String?,
    val specialRequests: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class UpdateBookingStatusRequest(
    val status: String
)

