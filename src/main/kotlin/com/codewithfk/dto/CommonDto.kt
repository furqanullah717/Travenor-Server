package com.codewithfk.dto

import kotlinx.serialization.Serializable

// Common response DTOs used across multiple endpoints

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class MessageResponse(
    val message: String
)

@Serializable
data class ListingSearchResponse(
    val listings: List<ListingResponse>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)

@Serializable
data class AvailabilityCheckResponse(
    val available: Boolean,
    val reason: String? = null,
    val priceCalculation: PriceCalculationResponse? = null
)

@Serializable
data class PriceCalculationResponse(
    val basePrice: Double,
    val tax: Double,
    val serviceFee: Double,
    val total: Double,
    val currency: String,
    val nights: Int,
    val numberOfGuests: Int
)

@Serializable
data class ImageInfo(
    val url: String,
    val fileName: String,
    val size: Long,
    val contentType: String? = null
)

@Serializable
data class ImageUploadResponse(
    val message: String,
    val images: List<ImageInfo>
)

@Serializable
data class SingleImageUploadResponse(
    val message: String,
    val image: ImageInfo
)

@Serializable
data class ImagesListResponse(
    val images: List<String>
)

@Serializable
data class PaymentIntentStatusResponse(
    val id: String,
    val status: String,
    val amount: Long,
    val currency: String
)

@Serializable
data class WebhookResponse(
    val received: Boolean
)

@Serializable
data class ValidationErrorResponse(
    val error: String,
    val details: List<String>
)

