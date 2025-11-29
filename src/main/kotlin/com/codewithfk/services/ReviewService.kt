package com.codewithfk.services

import com.codewithfk.database.DatabaseFactory
import com.codewithfk.dto.ReviewResponse
import com.codewithfk.models.Reviews
import com.codewithfk.models.TravelListings
import com.codewithfk.models.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.math.BigDecimal
import kotlinx.datetime.Clock
import java.util.UUID

class ReviewService {
    suspend fun createReview(
        customerId: String,
        listingId: String,
        bookingId: String?,
        rating: Int,
        title: String?,
        comment: String?
    ): ReviewResponse = DatabaseFactory.dbQuery {
        if (rating < 1 || rating > 5) {
            throw IllegalArgumentException("Rating must be between 1 and 5")
        }
        
        val id = Reviews.insert {
            it[Reviews.customerId] = UUID.fromString(customerId)
            it[Reviews.listingId] = UUID.fromString(listingId)
            it[Reviews.bookingId] = bookingId?.let { UUID.fromString(it) }
            it[Reviews.rating] = rating
            it[Reviews.title] = title
            it[Reviews.comment] = comment
            it[Reviews.isVerified] = bookingId != null
        }[Reviews.id].value
        
        // Update listing rating
        updateListingRating(listingId)
        
        getReviewById(id.toString())!!
    }
    
    suspend fun getReviewById(id: String): ReviewResponse? = DatabaseFactory.dbQuery {
        Reviews.join(Users, JoinType.INNER, Reviews.customerId, Users.id)
            .select { Reviews.id eq UUID.fromString(id) }
            .map { rowToReview(it) }
            .singleOrNull()
    }
    
    suspend fun getReviewsByListing(listingId: String, page: Int = 1, pageSize: Int = 20): List<ReviewResponse> =
        DatabaseFactory.dbQuery {
            Reviews.join(Users, JoinType.INNER, Reviews.customerId, Users.id)
                .select { Reviews.listingId eq UUID.fromString(listingId) }
                .andWhere { Reviews.isApproved eq true }
                .orderBy(Reviews.createdAt, SortOrder.DESC)
                .limit(pageSize, offset = ((page - 1) * pageSize).toLong())
                .map { rowToReview(it) }
        }
    
    suspend fun getReviewsByCustomer(customerId: String, page: Int = 1, pageSize: Int = 20): List<ReviewResponse> =
        DatabaseFactory.dbQuery {
            Reviews.join(Users, JoinType.INNER, Reviews.customerId, Users.id)
                .select { Reviews.customerId eq UUID.fromString(customerId) }
                .orderBy(Reviews.createdAt, SortOrder.DESC)
                .limit(pageSize, offset = ((page - 1) * pageSize).toLong())
                .map { rowToReview(it) }
        }
    
    suspend fun updateReview(id: String, rating: Int?, title: String?, comment: String?): ReviewResponse? =
        DatabaseFactory.dbQuery {
            if (rating != null && (rating < 1 || rating > 5)) {
                throw IllegalArgumentException("Rating must be between 1 and 5")
            }
            
            val review = Reviews.select { Reviews.id eq UUID.fromString(id) }.singleOrNull()
            val listingId = review?.get(Reviews.listingId)?.toString()
            
            Reviews.update({ Reviews.id eq UUID.fromString(id) }) {
                if (rating != null) it[Reviews.rating] = rating
                if (title != null) it[Reviews.title] = title
                if (comment != null) it[Reviews.comment] = comment
                it[Reviews.updatedAt] = Clock.System.now()
            }
            
            listingId?.let { updateListingRating(it) }
            
            getReviewById(id)
        }
    
    suspend fun deleteReview(id: String): Boolean = DatabaseFactory.dbQuery {
        val review = Reviews.select { Reviews.id eq UUID.fromString(id) }.singleOrNull()
        val listingId = review?.get(Reviews.listingId)?.toString()
        
        val deleted = Reviews.deleteWhere { Reviews.id eq UUID.fromString(id) } > 0
        
        if (deleted && listingId != null) {
            updateListingRating(listingId)
        }
        
        deleted
    }
    
    suspend fun approveReview(id: String): ReviewResponse? = DatabaseFactory.dbQuery {
        Reviews.update({ Reviews.id eq UUID.fromString(id) }) {
            it[Reviews.isApproved] = true
            it[Reviews.updatedAt] = Clock.System.now()
        }
        getReviewById(id)
    }
    
    private suspend fun updateListingRating(listingId: String) = DatabaseFactory.dbQuery {
        val reviews = Reviews.select { 
            (Reviews.listingId eq UUID.fromString(listingId)) and (Reviews.isApproved eq true)
        }
        
        val ratings = reviews.map { it[Reviews.rating] }
        if (ratings.isNotEmpty()) {
            val averageRating = ratings.average()
            val reviewCount = ratings.size
            
            TravelListings.update({ TravelListings.id eq UUID.fromString(listingId) }) {
                it[TravelListings.rating] = BigDecimal.valueOf(averageRating)
                it[TravelListings.reviewCount] = reviewCount
            }
        }
    }
    
    private fun rowToReview(row: ResultRow): ReviewResponse {
        val firstName = row.getOrNull(Users.firstName)
        val lastName = row.getOrNull(Users.lastName)
        val customerName = if (firstName != null || lastName != null) {
            "$firstName $lastName".trim()
        } else null
        
        return ReviewResponse(
            id = row[Reviews.id].toString(),
            customerId = row[Reviews.customerId].toString(),
            listingId = row[Reviews.listingId].toString(),
            bookingId = row.getOrNull(Reviews.bookingId)?.toString(),
            rating = row[Reviews.rating],
            title = row[Reviews.title],
            comment = row[Reviews.comment],
            isVerified = row[Reviews.isVerified],
            isApproved = row[Reviews.isApproved],
            createdAt = row[Reviews.createdAt].toString(),
            updatedAt = row[Reviews.updatedAt].toString(),
            customerName = customerName
        )
    }
}

