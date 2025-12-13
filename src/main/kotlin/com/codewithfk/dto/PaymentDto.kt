package com.codewithfk.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreatePaymentIntentRequest(
    val bookingId: String,
    val amount: Long? = null, // If null, uses booking total
    val currency: String = "usd"
)

@Serializable
data class PaymentIntentResponse(
    val clientSecret: String,
    val paymentIntentId: String,
    val amount: Long,
    val currency: String,
    val status: String
)

@Serializable
data class RefundRequest(
    val bookingId: String,
    val amount: Long? = null, // If null, full refund
    val reason: String? = null // duplicate, fraudulent, requested_by_customer
)

@Serializable
data class RefundResponse(
    val refundId: String,
    val amount: Long,
    val currency: String,
    val status: String,
    val reason: String?
)


