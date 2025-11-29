package com.codewithfk.services

import com.codewithfk.database.DatabaseFactory
import com.codewithfk.models.BookingStatus
import com.codewithfk.models.Bookings
import com.codewithfk.models.TravelListings
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

class BookingAvailabilityService {
    suspend fun checkAvailability(
        listingId: String,
        checkInDate: Instant?,
        checkOutDate: Instant?,
        numberOfGuests: Int
    ): AvailabilityResult = DatabaseFactory.dbQuery {
        val listing = TravelListings.select { TravelListings.id eq UUID.fromString(listingId) }.singleOrNull()
            ?: return@dbQuery AvailabilityResult(
                available = false,
                reason = "Listing not found"
            )
        
        // Check if listing is active
        if (!listing[TravelListings.isActive]) {
            return@dbQuery AvailabilityResult(
                available = false,
                reason = "Listing is not active"
            )
        }
        
        // Check capacity
        val capacity = listing[TravelListings.capacity]
        if (capacity != null && numberOfGuests > capacity) {
            return@dbQuery AvailabilityResult(
                available = false,
                reason = "Number of guests exceeds capacity (max: $capacity)"
            )
        }
        
        // Check date availability if dates are provided
        if (checkInDate != null && checkOutDate != null) {
            // Check if listing has availability window
            val availableFrom = listing[TravelListings.availableFrom]
            val availableTo = listing[TravelListings.availableTo]
            
            if (availableFrom != null && checkInDate < availableFrom) {
                return@dbQuery AvailabilityResult(
                    available = false,
                    reason = "Check-in date is before listing availability start"
                )
            }
            
            if (availableTo != null && checkOutDate > availableTo) {
                return@dbQuery AvailabilityResult(
                    available = false,
                    reason = "Check-out date is after listing availability end"
                )
            }
            
            if (checkInDate >= checkOutDate) {
                return@dbQuery AvailabilityResult(
                    available = false,
                    reason = "Check-out date must be after check-in date"
                )
            }
            
            // Check for conflicting bookings
            val conflictingBookings = Bookings.select {
                (Bookings.listingId eq UUID.fromString(listingId)) and
                (Bookings.status.inList(listOf(BookingStatus.PENDING.name, BookingStatus.CONFIRMED.name))) and
                (
                    ((Bookings.checkInDate lessEq checkInDate) and (Bookings.checkOutDate greaterEq checkInDate)) or
                    ((Bookings.checkInDate lessEq checkOutDate) and (Bookings.checkOutDate greaterEq checkOutDate)) or
                    ((Bookings.checkInDate greaterEq checkInDate) and (Bookings.checkOutDate lessEq checkOutDate))
                )
            }.count()
            
            if (conflictingBookings > 0) {
                return@dbQuery AvailabilityResult(
                    available = false,
                    reason = "Selected dates are not available (conflicting bookings)"
                )
            }
        }
        
        AvailabilityResult(
            available = true,
            reason = null
        )
    }
    
    suspend fun calculatePrice(
        listingId: String,
        checkInDate: Instant?,
        checkOutDate: Instant?,
        numberOfGuests: Int
    ): PriceCalculation = DatabaseFactory.dbQuery {
        val listing = TravelListings.select { TravelListings.id eq UUID.fromString(listingId) }.singleOrNull()
            ?: throw IllegalArgumentException("Listing not found")
        
        val basePrice = listing[TravelListings.price].toDouble()
        val currency = listing[TravelListings.currency]
        
        // Calculate base price based on guests
        var totalPrice = basePrice * numberOfGuests
        
        // Calculate nights if dates are provided
        var nights = 1
        if (checkInDate != null && checkOutDate != null) {
            val duration = checkOutDate.toEpochMilliseconds() - checkInDate.toEpochMilliseconds()
            nights = (duration / (1000 * 60 * 60 * 24)).toInt()
            if (nights < 1) nights = 1
            
            // For hotels, price is per night
            if (listing[TravelListings.category] == "HOTEL") {
                totalPrice = basePrice * nights * numberOfGuests
            }
        }
        
        // Calculate taxes (example: 10% tax)
        val taxRate = 0.10
        val tax = totalPrice * taxRate
        
        // Service fee (example: 5%)
        val serviceFeeRate = 0.05
        val serviceFee = totalPrice * serviceFeeRate
        
        val total = totalPrice + tax + serviceFee
        
        PriceCalculation(
            basePrice = totalPrice,
            tax = tax,
            serviceFee = serviceFee,
            total = total,
            currency = currency,
            nights = nights,
            numberOfGuests = numberOfGuests
        )
    }
}

@Serializable
data class AvailabilityResult(
    val available: Boolean,
    val reason: String?
)

@Serializable
data class PriceCalculation(
    val basePrice: Double,
    val tax: Double,
    val serviceFee: Double,
    val total: Double,
    val currency: String,
    val nights: Int,
    val numberOfGuests: Int
)

