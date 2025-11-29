package com.codewithfk.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateReviewRequest(
    val listingId: String,
    val bookingId: String? = null,
    val rating: Int,
    val title: String? = null,
    val comment: String? = null
)

@Serializable
data class ReviewResponse(
    val id: String,
    val customerId: String,
    val listingId: String,
    val bookingId: String?,
    val rating: Int,
    val title: String?,
    val comment: String?,
    val isVerified: Boolean,
    val isApproved: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val customerName: String? = null
)

