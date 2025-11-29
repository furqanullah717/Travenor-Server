package com.codewithfk.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateBookingRequest(
    val listingId: String,
    val checkInDate: String? = null,
    val checkOutDate: String? = null,
    val numberOfGuests: Int = 1,
    val specialRequests: String? = null
)

@Serializable
data class BookingResponse(
    val id: String,
    val customerId: String,
    val listingId: String,
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

