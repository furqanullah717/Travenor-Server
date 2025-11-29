package com.codewithfk.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.codewithfk.dto.UserResponse
import io.ktor.server.config.*
import java.util.*

object JwtConfig {
    private lateinit var secret: String
    private lateinit var issuer: String
    private lateinit var audience: String
    private lateinit var realm: String
    
    fun init(config: ApplicationConfig) {
        secret = config.property("jwt.secret").getString()
        issuer = config.property("jwt.issuer").getString()
        audience = config.property("jwt.audience").getString()
        realm = config.property("jwt.realm").getString()
    }
    
    fun generateToken(user: UserResponse): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", user.id)
            .withClaim("email", user.email)
            .withClaim("role", user.role)
            .withExpiresAt(Date(System.currentTimeMillis() + 86400000)) // 24 hours
            .sign(Algorithm.HMAC256(secret))
    }
    
    fun getSecret() = secret
    fun getIssuer() = issuer
    fun getAudience() = audience
    fun getRealm() = realm
}

