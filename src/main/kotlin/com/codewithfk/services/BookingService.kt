package com.codewithfk.services

import com.codewithfk.database.DatabaseFactory
import com.codewithfk.dto.BookingResponse
import com.codewithfk.models.BookingStatus
import com.codewithfk.models.Bookings
import com.codewithfk.models.PaymentStatus
import com.codewithfk.models.TravelListings
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.math.BigDecimal
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.util.UUID

class BookingService {
    suspend fun createBooking(
        customerId: String,
        listingId: String,
        checkInDate: String?,
        checkOutDate: String?,
        numberOfGuests: Int,
        specialRequests: String?,
        totalPrice: BigDecimal? = null // If provided, use this; otherwise calculate
    ): BookingResponse = DatabaseFactory.dbQuery {
        // Get listing price
        val listing = TravelListings.select { TravelListings.id eq UUID.fromString(listingId) }.singleOrNull()
            ?: throw IllegalArgumentException("Listing not found")
        
        val calculatedPrice = totalPrice ?: run {
            val basePrice = listing[TravelListings.price]
            // For hotels, calculate based on nights
            val checkIn = checkInDate?.let { Instant.parse(it) }
            val checkOut = checkOutDate?.let { Instant.parse(it) }
            
            if (checkIn != null && checkOut != null && listing[TravelListings.category] == "HOTEL") {
                val nights = ((checkOut.toEpochMilliseconds() - checkIn.toEpochMilliseconds()) / (1000 * 60 * 60 * 24)).toInt()
                basePrice.multiply(BigDecimal.valueOf(nights.toLong())).multiply(BigDecimal.valueOf(numberOfGuests.toLong()))
            } else {
                basePrice.multiply(BigDecimal.valueOf(numberOfGuests.toLong()))
            }
        }
        
        val id = Bookings.insert {
            it[Bookings.customerId] = UUID.fromString(customerId)
            it[Bookings.listingId] = UUID.fromString(listingId)
            it[Bookings.checkInDate] = checkInDate?.let { Instant.parse(it) }
            it[Bookings.checkOutDate] = checkOutDate?.let { Instant.parse(it) }
            it[Bookings.numberOfGuests] = numberOfGuests
            it[Bookings.totalPrice] = calculatedPrice
            it[Bookings.currency] = listing[TravelListings.currency]
            it[Bookings.status] = BookingStatus.PENDING.name
            it[Bookings.paymentStatus] = PaymentStatus.PENDING.name
            it[Bookings.specialRequests] = specialRequests
        }[Bookings.id].value
        
        getBookingById(id.toString())!!
    }
    
    suspend fun getBookingById(id: String): BookingResponse? = DatabaseFactory.dbQuery {
        Bookings.select { Bookings.id eq UUID.fromString(id) }
            .map { rowToBooking(it) }
            .singleOrNull()
    }
    
    suspend fun getBookingsByCustomer(customerId: String, page: Int = 1, pageSize: Int = 20): List<BookingResponse> =
        DatabaseFactory.dbQuery {
            Bookings.select { Bookings.customerId eq UUID.fromString(customerId) }
                .orderBy(Bookings.createdAt, SortOrder.DESC)
                .limit(pageSize, offset = ((page - 1) * pageSize).toLong())
                .map { rowToBooking(it) }
        }
    
    suspend fun getBookingsByListing(listingId: String, page: Int = 1, pageSize: Int = 20): List<BookingResponse> =
        DatabaseFactory.dbQuery {
            Bookings.select { Bookings.listingId eq UUID.fromString(listingId) }
                .orderBy(Bookings.createdAt, SortOrder.DESC)
                .limit(pageSize, offset = ((page - 1) * pageSize).toLong())
                .map { rowToBooking(it) }
        }
    
    suspend fun updateBookingStatus(id: String, status: String, paymentStatus: String? = null): BookingResponse? =
        DatabaseFactory.dbQuery {
            Bookings.update({ Bookings.id eq UUID.fromString(id) }) {
                try {
                    it[Bookings.status] = BookingStatus.valueOf(status.uppercase()).name
                } catch (e: IllegalArgumentException) {
                    // Invalid status, ignore
                }
                if (paymentStatus != null) {
                    try {
                        it[Bookings.paymentStatus] = PaymentStatus.valueOf(paymentStatus.uppercase()).name
                    } catch (e: IllegalArgumentException) {
                        // Invalid payment status, ignore
                    }
                }
                it[Bookings.updatedAt] = Clock.System.now()
            }
            getBookingById(id)
        }
    
    suspend fun updatePaymentStatus(id: String, paymentStatus: String, paymentId: String? = null): BookingResponse? =
        DatabaseFactory.dbQuery {
            Bookings.update({ Bookings.id eq UUID.fromString(id) }) {
                try {
                    it[Bookings.paymentStatus] = PaymentStatus.valueOf(paymentStatus.uppercase()).name
                } catch (e: IllegalArgumentException) {
                    // Invalid payment status, ignore
                }
                if (paymentId != null) it[Bookings.paymentId] = paymentId
                it[Bookings.updatedAt] = Clock.System.now()
            }
            getBookingById(id)
        }
    
    suspend fun cancelBooking(id: String, customerId: String): BookingResponse? = DatabaseFactory.dbQuery {
        val booking = Bookings.select { 
            (Bookings.id eq UUID.fromString(id)) and (Bookings.customerId eq UUID.fromString(customerId))
        }.singleOrNull()
        
        booking?.let {
            Bookings.update({ Bookings.id eq UUID.fromString(id) }) {
                it[Bookings.status] = BookingStatus.CANCELLED.name
                it[Bookings.updatedAt] = Clock.System.now()
            }
            getBookingById(id)
        }
    }
    
    private fun rowToBooking(row: ResultRow): BookingResponse {
        return BookingResponse(
            id = row[Bookings.id].toString(),
            customerId = row[Bookings.customerId].toString(),
            listingId = row[Bookings.listingId].toString(),
            checkInDate = row[Bookings.checkInDate]?.toString(),
            checkOutDate = row[Bookings.checkOutDate]?.toString(),
            numberOfGuests = row[Bookings.numberOfGuests],
            totalPrice = row[Bookings.totalPrice].toDouble(),
            currency = row[Bookings.currency],
            status = row[Bookings.status],
            paymentStatus = row[Bookings.paymentStatus],
            paymentId = row[Bookings.paymentId],
            specialRequests = row[Bookings.specialRequests],
            createdAt = row[Bookings.createdAt].toString(),
            updatedAt = row[Bookings.updatedAt].toString()
        )
    }
}

