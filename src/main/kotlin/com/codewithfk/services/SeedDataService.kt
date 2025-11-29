package com.codewithfk.services

import com.codewithfk.database.DatabaseFactory
import com.codewithfk.models.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.mindrot.jbcrypt.BCrypt
import java.math.BigDecimal
import java.util.UUID

class SeedDataService {
    suspend fun seedDatabase() {
        DatabaseFactory.dbQuery {
            // Seed Users
            val adminId = seedAdmin()
            val vendor1Id = seedVendor1()
            val vendor2Id = seedVendor2()
            val customer1Id = seedCustomer1()
            val customer2Id = seedCustomer2()
            
            // Seed Listings
            seedListings(adminId, vendor1Id, vendor2Id)
            
            println("✅ Database seeded successfully!")
            println("📝 Test Users Created:")
            println("   Admin: admin@trevnor.com / password123")
            println("   Vendor 1: vendor1@trevnor.com / password123")
            println("   Vendor 2: vendor2@trevnor.com / password123")
            println("   Customer 1: customer1@trevnor.com / password123")
            println("   Customer 2: customer2@trevnor.com / password123")
        }
    }
    
    private suspend fun seedAdmin(): UUID = DatabaseFactory.dbQuery {
        val existing = Users.select { Users.email eq "admin@trevnor.com" }.singleOrNull()
        if (existing != null) {
            return@dbQuery existing[Users.id].value
        }
        
        Users.insert {
            it[email] = "admin@trevnor.com"
            it[passwordHash] = BCrypt.hashpw("password123", BCrypt.gensalt())
            it[firstName] = "Admin"
            it[lastName] = "User"
            it[role] = UserRole.ADMIN.name
            it[isActive] = true
        }[Users.id].value
    }
    
    private suspend fun seedVendor1(): UUID = DatabaseFactory.dbQuery {
        val existing = Users.select { Users.email eq "vendor1@trevnor.com" }.singleOrNull()
        if (existing != null) {
            return@dbQuery existing[Users.id].value
        }
        
        Users.insert {
            it[email] = "vendor1@trevnor.com"
            it[passwordHash] = BCrypt.hashpw("password123", BCrypt.gensalt())
            it[firstName] = "John"
            it[lastName] = "Vendor"
            it[phone] = "+1234567890"
            it[role] = UserRole.VENDOR.name
            it[isActive] = true
        }[Users.id].value
    }
    
    private suspend fun seedVendor2(): UUID = DatabaseFactory.dbQuery {
        val existing = Users.select { Users.email eq "vendor2@trevnor.com" }.singleOrNull()
        if (existing != null) {
            return@dbQuery existing[Users.id].value
        }
        
        Users.insert {
            it[email] = "vendor2@trevnor.com"
            it[passwordHash] = BCrypt.hashpw("password123", BCrypt.gensalt())
            it[firstName] = "Sarah"
            it[lastName] = "Travel"
            it[phone] = "+1234567891"
            it[role] = UserRole.VENDOR.name
            it[isActive] = true
        }[Users.id].value
    }
    
    private suspend fun seedCustomer1(): UUID = DatabaseFactory.dbQuery {
        val existing = Users.select { Users.email eq "customer1@trevnor.com" }.singleOrNull()
        if (existing != null) {
            return@dbQuery existing[Users.id].value
        }
        
        Users.insert {
            it[email] = "customer1@trevnor.com"
            it[passwordHash] = BCrypt.hashpw("password123", BCrypt.gensalt())
            it[firstName] = "Alice"
            it[lastName] = "Customer"
            it[phone] = "+1234567892"
            it[role] = UserRole.CUSTOMER.name
            it[isActive] = true
        }[Users.id].value
    }
    
    private suspend fun seedCustomer2(): UUID = DatabaseFactory.dbQuery {
        val existing = Users.select { Users.email eq "customer2@trevnor.com" }.singleOrNull()
        if (existing != null) {
            return@dbQuery existing[Users.id].value
        }
        
        Users.insert {
            it[email] = "customer2@trevnor.com"
            it[passwordHash] = BCrypt.hashpw("password123", BCrypt.gensalt())
            it[firstName] = "Bob"
            it[lastName] = "Traveler"
            it[phone] = "+1234567893"
            it[role] = UserRole.CUSTOMER.name
            it[isActive] = true
        }[Users.id].value
    }
    
    private suspend fun seedListings(adminId: UUID, vendor1Id: UUID, vendor2Id: UUID) = DatabaseFactory.dbQuery {
        // Hotel listings
        createListing(
            vendorId = vendor1Id,
            title = "Luxury Beach Resort - Maldives",
            description = "Experience paradise at our 5-star beach resort with private villas, world-class dining, and pristine beaches.",
            category = ListingCategory.HOTEL,
            location = "Malé, Maldives",
            city = "Malé",
            country = "Maldives",
            price = BigDecimal("299.99"),
            currency = "USD",
            capacity = 4,
            availableFrom = Clock.System.now(),
            availableTo = null,
            images = listOf(
                "https://images.unsplash.com/photo-1571896349842-33c89424de2d?w=800",
                "https://images.unsplash.com/photo-1551882547-ff40c63fe5fa?w=800"
            ),
            amenities = listOf("WiFi", "Pool", "Spa", "Beach Access", "Restaurant", "Bar")
        )
        
        createListing(
            vendorId = vendor1Id,
            title = "Mountain View Hotel - Swiss Alps",
            description = "Cozy alpine hotel with stunning mountain views, perfect for skiing and hiking enthusiasts.",
            category = ListingCategory.HOTEL,
            location = "Zermatt, Switzerland",
            city = "Zermatt",
            country = "Switzerland",
            price = BigDecimal("199.99"),
            currency = "USD",
            capacity = 2,
            availableFrom = Clock.System.now(),
            availableTo = null,
            images = listOf(
                "https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?w=800"
            ),
            amenities = listOf("WiFi", "Ski Storage", "Restaurant", "Fireplace", "Mountain View")
        )
        
        // Flight listings
        createListing(
            vendorId = vendor2Id,
            title = "Round Trip to Paris",
            description = "Economy class round trip flight from New York to Paris with flexible dates.",
            category = ListingCategory.FLIGHT,
            location = "New York to Paris",
            city = "Paris",
            country = "France",
            price = BigDecimal("899.99"),
            currency = "USD",
            capacity = null,
            availableFrom = Clock.System.now(),
            availableTo = null,
            images = listOf(
                "https://images.unsplash.com/photo-1436491865332-7a61a109cc05?w=800"
            ),
            amenities = listOf("Meal Included", "Entertainment", "WiFi", "Seat Selection")
        )
        
        // Activity listings
        createListing(
            vendorId = vendor2Id,
            title = "Safari Adventure - Serengeti",
            description = "3-day guided safari tour through Serengeti National Park with professional guides and luxury camping.",
            category = ListingCategory.ACTIVITY,
            location = "Serengeti National Park, Tanzania",
            city = "Arusha",
            country = "Tanzania",
            price = BigDecimal("1299.99"),
            currency = "USD",
            capacity = 6,
            availableFrom = Clock.System.now(),
            availableTo = null,
            images = listOf(
                "https://images.unsplash.com/photo-1516426122078-c23e76319801?w=800"
            ),
            amenities = listOf("Guide", "Transportation", "Meals", "Camping Equipment", "Wildlife Viewing")
        )
        
        createListing(
            vendorId = vendor1Id,
            title = "Tokyo City Tour",
            description = "Full-day guided tour of Tokyo's most famous landmarks including temples, markets, and modern districts.",
            category = ListingCategory.ACTIVITY,
            location = "Tokyo, Japan",
            city = "Tokyo",
            country = "Japan",
            price = BigDecimal("149.99"),
            currency = "USD",
            capacity = 20,
            availableFrom = Clock.System.now(),
            availableTo = null,
            images = listOf(
                "https://images.unsplash.com/photo-1540959733332-eab4deabeeaf?w=800"
            ),
            amenities = listOf("Guide", "Transportation", "Entry Fees", "Lunch")
        )
        
        // Package listing
        createListing(
            vendorId = adminId,
            title = "Complete Bali Experience Package",
            description = "7-day all-inclusive package: flights, hotel, tours, and meals. The perfect Bali getaway!",
            category = ListingCategory.PACKAGE,
            location = "Bali, Indonesia",
            city = "Denpasar",
            country = "Indonesia",
            price = BigDecimal("2499.99"),
            currency = "USD",
            capacity = 2,
            availableFrom = Clock.System.now(),
            availableTo = null,
            images = listOf(
                "https://images.unsplash.com/photo-1537996194471-e657df975ab4?w=800"
            ),
            amenities = listOf("Flights", "Hotel", "Tours", "Meals", "Airport Transfer", "Travel Insurance")
        )
    }
    
    private suspend fun createListing(
        vendorId: UUID,
        title: String,
        description: String,
        category: ListingCategory,
        location: String,
        city: String?,
        country: String?,
        price: BigDecimal,
        currency: String,
        capacity: Int?,
        availableFrom: kotlinx.datetime.Instant?,
        availableTo: kotlinx.datetime.Instant?,
        images: List<String>?,
        amenities: List<String>?
    ) = DatabaseFactory.dbQuery {
        val existing = TravelListings.select { 
            (TravelListings.vendorId eq vendorId) and (TravelListings.title eq title)
        }.singleOrNull()
        
        if (existing != null) {
            return@dbQuery
        }
        
        val imagesJson = images?.let {
            buildJsonArray { 
                images.forEach { img -> add(img) } 
            }.toString()
        }
        
        val amenitiesJson = amenities?.let {
            buildJsonArray { 
                amenities.forEach { am -> add(am) } 
            }.toString()
        }
        
        TravelListings.insert {
            it[TravelListings.vendorId] = vendorId
            it[TravelListings.title] = title
            it[TravelListings.description] = description
            it[TravelListings.category] = category.name
            it[TravelListings.location] = location
            it[TravelListings.city] = city
            it[TravelListings.country] = country
            it[TravelListings.price] = price
            it[TravelListings.currency] = currency
            it[TravelListings.capacity] = capacity
            it[TravelListings.availableFrom] = availableFrom
            it[TravelListings.availableTo] = availableTo
            it[TravelListings.images] = imagesJson
            it[TravelListings.amenities] = amenitiesJson
            it[TravelListings.isActive] = true
        }
    }
}

