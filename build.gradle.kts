plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.codewithfk"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    // Ktor Core
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.config.yaml)
    
    // Logging
    implementation(libs.logback.classic)
    
    // Content Negotiation
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    
    // Authentication
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    
    // CORS
    implementation(libs.ktor.server.cors)
    
    // Status Pages
    implementation(libs.ktor.server.status.pages)
    
    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.h2.database)
    implementation(libs.postgresql.driver)
    implementation(libs.mysql.driver)
    implementation(libs.hikaricp)
    
    // Security
    implementation(libs.bcrypt)
    implementation(libs.jwt)
    
    // Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Swagger/OpenAPI
    implementation(libs.swagger.core)
    implementation(libs.swagger.models)
    implementation(libs.swagger.ui)
    
    // Stripe
    implementation(libs.stripe.java)
    
    // Firebase
    implementation(libs.firebase.admin)
    
    // Testing
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}
