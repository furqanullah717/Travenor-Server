package com.codewithfk.services

import com.codewithfk.database.DatabaseFactory
import com.codewithfk.models.TravelListings
import io.ktor.server.config.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.io.File
import java.util.UUID
import kotlin.io.path.*

class ImageService {
    private var uploadDirectory: String = "./uploads"
    private var baseUrl: String = "http://localhost:8080"
    private var maxFileSizeBytes: Long = 10 * 1024 * 1024 // 10MB default
    private val allowedMimeTypes = setOf("image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif")
    
    fun init(config: ApplicationConfig) {
        uploadDirectory = config.property("uploads.directory").getString()
        baseUrl = config.property("uploads.baseUrl").getString()
        val maxFileSizeMB = config.property("uploads.maxFileSize").getString().toIntOrNull() ?: 10
        maxFileSizeBytes = maxFileSizeMB * 1024L * 1024L
        
        // Create upload directory if it doesn't exist
        val uploadDir = File(uploadDirectory)
        if (!uploadDir.exists()) {
            uploadDir.mkdirs()
        }
        
        // Create subdirectories
        File(uploadDir, "listings").mkdirs()
        File(uploadDir, "users").mkdirs()
    }
    
    suspend fun uploadImage(
        fileBytes: ByteArray,
        fileName: String,
        contentType: String,
        listingId: String? = null,
        userId: String? = null
    ): ImageUploadResult {
        // Validate file type
        if (!allowedMimeTypes.contains(contentType.lowercase())) {
            throw ImageUploadException("Invalid file type. Allowed types: ${allowedMimeTypes.joinToString()}")
        }
        
        // Validate file size
        if (fileBytes.size > maxFileSizeBytes) {
            throw ImageUploadException("File size exceeds maximum allowed size of ${maxFileSizeBytes / (1024 * 1024)}MB")
        }
        
        // Generate unique filename
        val fileExtension = fileName.substringAfterLast('.', "")
        val uniqueFileName = "${UUID.randomUUID()}.$fileExtension"
        
        // Determine subdirectory
        val subdirectory = when {
            listingId != null -> "listings"
            userId != null -> "users"
            else -> "general"
        }
        
        val targetDir = File(uploadDirectory, subdirectory)
        targetDir.mkdirs()
        
        val targetFile = File(targetDir, uniqueFileName)
        targetFile.writeBytes(fileBytes)
        
        // Generate URL
        val imageUrl = "$baseUrl/uploads/$subdirectory/$uniqueFileName"
        
        // If associated with listing, add to listing's images
        if (listingId != null) {
            addImageToListing(listingId, imageUrl)
        }
        
        return ImageUploadResult(
            url = imageUrl,
            fileName = uniqueFileName,
            originalFileName = fileName,
            size = fileBytes.size,
            contentType = contentType
        )
    }
    
    suspend fun deleteImage(imageUrl: String, listingId: String? = null): Boolean = DatabaseFactory.dbQuery {
        try {
            // Extract filename from URL
            val fileName = imageUrl.substringAfterLast("/")
            val subdirectory = when {
                imageUrl.contains("/listings/") -> "listings"
                imageUrl.contains("/users/") -> "users"
                else -> "general"
            }
            
            val file = File(uploadDirectory, "$subdirectory/$fileName")
            
            // Remove from listing if associated
            if (listingId != null) {
                removeImageFromListing(listingId, imageUrl)
            }
            
            // Delete file
            if (file.exists()) {
                file.delete()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun getListingImages(listingId: String): List<String> = DatabaseFactory.dbQuery {
        val listing = TravelListings.select { TravelListings.id eq UUID.fromString(listingId) }.singleOrNull()
            ?: return@dbQuery emptyList()
        
        val imagesJson = listing[TravelListings.images] ?: return@dbQuery emptyList()
        
        try {
            Json.parseToJsonElement(imagesJson).jsonArray.map { element -> element.jsonPrimitive.content }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private suspend fun addImageToListing(listingId: String, imageUrl: String) = DatabaseFactory.dbQuery {
        val listing = TravelListings.select { TravelListings.id eq UUID.fromString(listingId) }.singleOrNull()
            ?: return@dbQuery
        
        val currentImagesJson = listing[TravelListings.images]
        val currentImages = if (currentImagesJson != null) {
            try {
                Json.parseToJsonElement(currentImagesJson).jsonArray.map { it.jsonPrimitive.content }.toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
        
        if (!currentImages.contains(imageUrl)) {
            currentImages.add(imageUrl)
            
            TravelListings.update({ TravelListings.id eq UUID.fromString(listingId) }) {
                it[TravelListings.images] = buildJsonArray { 
                    currentImages.forEach { img -> add(img) } 
                }.toString()
            }
        }
    }
    
    private suspend fun removeImageFromListing(listingId: String, imageUrl: String) = DatabaseFactory.dbQuery {
        val listing = TravelListings.select { TravelListings.id eq UUID.fromString(listingId) }.singleOrNull()
            ?: return@dbQuery
        
        val currentImagesJson = listing[TravelListings.images] ?: return@dbQuery
        val currentImages = try {
            Json.parseToJsonElement(currentImagesJson).jsonArray.map { it.jsonPrimitive.content }.toMutableList()
        } catch (e: Exception) {
            return@dbQuery
        }
        
        currentImages.remove(imageUrl)
        
        TravelListings.update({ TravelListings.id eq UUID.fromString(listingId) }) {
            it[TravelListings.images] = if (currentImages.isEmpty()) {
                null
            } else {
                buildJsonArray { currentImages.forEach { img -> add(img) } }.toString()
            }
        }
    }
    
    fun validateImageFile(fileName: String, contentType: String, size: Long): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Check file type
        if (!allowedMimeTypes.contains(contentType.lowercase())) {
            errors.add("Invalid file type. Allowed: ${allowedMimeTypes.joinToString()}")
        }
        
        // Check file size
        if (size > maxFileSizeBytes) {
            errors.add("File size ${size / (1024 * 1024)}MB exceeds maximum ${maxFileSizeBytes / (1024 * 1024)}MB")
        }
        
        // Check file extension
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val allowedExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")
        if (extension !in allowedExtensions) {
            errors.add("Invalid file extension. Allowed: ${allowedExtensions.joinToString()}")
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
}

data class ImageUploadResult(
    val url: String,
    val fileName: String,
    val originalFileName: String,
    val size: Int,
    val contentType: String
)

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

class ImageUploadException(message: String) : Exception(message)

