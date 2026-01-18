package com.codewithfk.dto

import kotlinx.serialization.Serializable

@Serializable
data class TripDateResponse(
    val id: String,
    val listingId: String,
    val startDate: String,
    val endDate: String,
    val maxCapacity: Int?,
    val currentBookings: Int,
    val availableSpots: Int?,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateTripDateRequest(
    val listingId: String,
    val startDate: String,
    val endDate: String,
    val maxCapacity: Int? = null
)

@Serializable
data class UpdateTripDateRequest(
    val startDate: String? = null,
    val endDate: String? = null,
    val maxCapacity: Int? = null,
    val isActive: Boolean? = null
)

