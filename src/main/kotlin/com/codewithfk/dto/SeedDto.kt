package com.codewithfk.dto

import kotlinx.serialization.Serializable

@Serializable
data class TestUserInfo(
    val email: String,
    val password: String,
    val role: String? = null
)

@Serializable
data class SeedResponse(
    val message: String,
    val testUsers: Map<String, TestUserInfo>,
    val note: String? = null
)

@Serializable
data class SeedInfoResponse(
    val message: String,
    val testUsers: Map<String, TestUserInfo>,
    val sampleListings: List<String>
)

