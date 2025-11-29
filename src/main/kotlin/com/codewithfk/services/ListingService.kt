package com.codewithfk.services

import com.codewithfk.database.DatabaseFactory
import com.codewithfk.dto.ListingResponse
import com.codewithfk.models.ListingCategory
import com.codewithfk.models.TravelListings
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.math.BigDecimal
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.time.format.DateTimeFormatter
import java.util.UUID

class ListingService {
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend fun createListing(
        vendorId: String,
        title: String,
        description: String,
        category: String,
        location: String,
        city: String?,
        country: String?,
        price: Double,
        currency: String,
        capacity: Int?,
        availableFrom: String?,
        availableTo: String?,
        images: List<String>?,
        amenities: List<String>?
    ): ListingResponse = DatabaseFactory.dbQuery {
        val listingCategory = try {
            ListingCategory.valueOf(category.uppercase())
        } catch (e: IllegalArgumentException) {
            ListingCategory.HOTEL
        }
        
        val id = TravelListings.insert {
            it[TravelListings.vendorId] = UUID.fromString(vendorId)
            it[TravelListings.title] = title
            it[TravelListings.description] = description
            it[TravelListings.category] = listingCategory.name
            it[TravelListings.location] = location
            it[TravelListings.city] = city
            it[TravelListings.country] = country
            it[TravelListings.price] = BigDecimal.valueOf(price)
            it[TravelListings.currency] = currency
            it[TravelListings.capacity] = capacity
            it[TravelListings.availableFrom] = availableFrom?.let { Instant.parse(it) }
            it[TravelListings.availableTo] = availableTo?.let { Instant.parse(it) }
            it[TravelListings.images] = images?.let { 
                buildJsonArray { images.forEach { img -> add(img) } }.toString()
            }
            it[TravelListings.amenities] = amenities?.let { 
                buildJsonArray { amenities.forEach { am -> add(am) } }.toString()
            }
        }[TravelListings.id].value
        
        getListingById(id.toString())!!
    }
    
    suspend fun getListingById(id: String): ListingResponse? = DatabaseFactory.dbQuery {
        TravelListings.select { TravelListings.id eq UUID.fromString(id) }
            .map { rowToListing(it) }
            .singleOrNull()
    }
    
    suspend fun updateListing(
        id: String,
        title: String?,
        description: String?,
        location: String?,
        city: String?,
        country: String?,
        price: Double?,
        currency: String?,
        capacity: Int?,
        availableFrom: String?,
        availableTo: String?,
        images: List<String>?,
        amenities: List<String>?,
        isActive: Boolean?
    ): ListingResponse? = DatabaseFactory.dbQuery {
        TravelListings.update({ TravelListings.id eq UUID.fromString(id) }) {
            if (title != null) it[TravelListings.title] = title
            if (description != null) it[TravelListings.description] = description
            if (location != null) it[TravelListings.location] = location
            if (city != null) it[TravelListings.city] = city
            if (country != null) it[TravelListings.country] = country
            if (price != null) it[TravelListings.price] = BigDecimal.valueOf(price)
            if (currency != null) it[TravelListings.currency] = currency
            if (capacity != null) it[TravelListings.capacity] = capacity
            if (availableFrom != null) it[TravelListings.availableFrom] = Instant.parse(availableFrom)
            if (availableTo != null) it[TravelListings.availableTo] = Instant.parse(availableTo)
            if (images != null) {
                it[TravelListings.images] = buildJsonArray { images.forEach { img -> add(img) } }.toString()
            }
            if (amenities != null) {
                it[TravelListings.amenities] = buildJsonArray { amenities.forEach { am -> add(am) } }.toString()
            }
            if (isActive != null) it[TravelListings.isActive] = isActive
            it[TravelListings.updatedAt] = Clock.System.now()
        }
        getListingById(id)
    }
    
    suspend fun searchListings(
        category: String?,
        location: String?,
        city: String?,
        country: String?,
        minPrice: Double?,
        maxPrice: Double?,
        minRating: Double?,
        page: Int,
        pageSize: Int
    ): Pair<List<ListingResponse>, Int> = DatabaseFactory.dbQuery {
        var query = TravelListings.select { TravelListings.isActive eq true }
        
        category?.let {
            try {
                val cat = ListingCategory.valueOf(it.uppercase())
                query = query.andWhere { TravelListings.category eq cat.name }
            } catch (e: IllegalArgumentException) {
                // Invalid category, ignore
            }
        }
        
        location?.let {
            query = query.andWhere { TravelListings.location like "%$it%" }
        }
        
        city?.let {
            query = query.andWhere { TravelListings.city eq it }
        }
        
        country?.let {
            query = query.andWhere { TravelListings.country eq it }
        }
        
        minPrice?.let {
            query = query.andWhere { TravelListings.price greaterEq BigDecimal.valueOf(it) }
        }
        
        maxPrice?.let {
            query = query.andWhere { TravelListings.price lessEq BigDecimal.valueOf(it) }
        }
        
        minRating?.let {
            query = query.andWhere { TravelListings.rating greaterEq BigDecimal.valueOf(it) }
        }
        
        val totalCount = query.count().toInt()
        val listings = query
            .orderBy(TravelListings.createdAt, SortOrder.DESC)
            .limit(pageSize, offset = ((page - 1) * pageSize).toLong())
            .map { rowToListing(it) }
        
        Pair(listings, totalCount)
    }
    
    suspend fun getListingsByVendor(vendorId: String, page: Int = 1, pageSize: Int = 20): List<ListingResponse> =
        DatabaseFactory.dbQuery {
            TravelListings.select { TravelListings.vendorId eq UUID.fromString(vendorId) }
                .orderBy(TravelListings.createdAt, SortOrder.DESC)
                .limit(pageSize, offset = ((page - 1) * pageSize).toLong())
                .map { rowToListing(it) }
        }
    
    suspend fun deleteListing(id: String): Boolean = DatabaseFactory.dbQuery {
        TravelListings.deleteWhere { TravelListings.id eq UUID.fromString(id) } > 0
    }
    
    private fun rowToListing(row: ResultRow): ListingResponse {
        val imagesJson = row[TravelListings.images]
        val amenitiesJson = row[TravelListings.amenities]
        
        val images = imagesJson?.let {
            try {
                json.parseToJsonElement(it).jsonArray.map { element -> element.jsonPrimitive.content }
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        val amenities = amenitiesJson?.let {
            try {
                json.parseToJsonElement(it).jsonArray.map { element -> element.jsonPrimitive.content }
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        return ListingResponse(
            id = row[TravelListings.id].toString(),
            vendorId = row[TravelListings.vendorId].toString(),
            title = row[TravelListings.title],
            description = row[TravelListings.description],
            category = row[TravelListings.category],
            location = row[TravelListings.location],
            city = row[TravelListings.city],
            country = row[TravelListings.country],
            price = row[TravelListings.price].toDouble(),
            currency = row[TravelListings.currency],
            capacity = row[TravelListings.capacity],
            availableFrom = row[TravelListings.availableFrom]?.toString(),
            availableTo = row[TravelListings.availableTo]?.toString(),
            images = images,
            amenities = amenities,
            rating = row[TravelListings.rating].toDouble(),
            reviewCount = row[TravelListings.reviewCount],
            isActive = row[TravelListings.isActive],
            createdAt = row[TravelListings.createdAt].toString(),
            updatedAt = row[TravelListings.updatedAt].toString()
        )
    }
}

