package com.codewithfk.routing

import com.codewithfk.dto.ErrorResponse
import com.codewithfk.dto.ImageUploadResponse
import com.codewithfk.dto.SingleImageUploadResponse
import com.codewithfk.dto.ImageInfo
import com.codewithfk.dto.ValidationErrorResponse
import com.codewithfk.dto.MessageResponse
import com.codewithfk.dto.ImagesListResponse
import com.codewithfk.services.ImageService
import com.codewithfk.services.ListingService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Route.imageRoutes(imageService: ImageService, listingService: ListingService) {
    // Public route to serve uploaded images
    route("/uploads") {
        get("/{subdirectory}/{filename}") {
            val subdirectory = call.parameters["subdirectory"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val filename = call.parameters["filename"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            
            // Security: Only allow specific subdirectories
            if (subdirectory !in listOf("listings", "users", "general")) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            
            val file = File("uploads/$subdirectory/$filename")
            if (!file.exists() || !file.isFile) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            
            // Set appropriate content type
            val contentType = when (filename.substringAfterLast('.').lowercase()) {
                "jpg", "jpeg" -> ContentType.Image.JPEG
                "png" -> ContentType.Image.PNG
                "webp" -> ContentType("image", "webp")
                "gif" -> ContentType.Image.GIF
                else -> ContentType.Application.OctetStream
            }
            
            val fileBytes = file.readBytes()
            call.respondBytes(fileBytes, contentType)
        }
    }
    
    // Authenticated image management routes
    route("/images") {
        authenticate("auth-jwt") {
            // Upload image for listing
            post("/listings/{listingId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                val role = principal?.payload?.getClaim("role")?.asString()
                val listingId = call.parameters["listingId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Listing ID required"))
                    return@post
                }
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                
                // Verify listing ownership or admin
                val listing = listingService.getListingById(listingId)
                if (listing == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Listing not found"))
                    return@post
                }
                
                if (listing.vendorId != userId && role != "ADMIN") {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Not authorized to upload images for this listing"))
                    return@post
                }
                
                try {
                    val multipartData = call.receiveMultipart()
                    var uploadedImages = mutableListOf<com.codewithfk.services.ImageUploadResult>()
                    
                    multipartData.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                val fileName = part.originalFileName ?: "image"
                                val contentType = part.contentType?.toString() ?: "application/octet-stream"
                                
                                // Validate before processing
                                val validation = imageService.validateImageFile(
                                    fileName = fileName,
                                    contentType = contentType,
                                    size = part.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
                                )
                                
                                if (!validation.isValid) {
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("error" to "Validation failed", "details" to validation.errors)
                                    )
                                    return@forEachPart
                                }
                                
                                // Read file bytes
                                val fileBytes = part.streamProvider().readBytes()
                                
                                // Upload image
                                val result = imageService.uploadImage(
                                    fileBytes = fileBytes,
                                    fileName = fileName,
                                    contentType = contentType,
                                    listingId = listingId
                                )
                                
                                uploadedImages.add(result)
                            }
                            else -> {}
                        }
                        part.dispose()
                    }
                    
                    if (uploadedImages.isEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("No image files provided"))
                        return@post
                    }
                    
                    call.respond(
                        HttpStatusCode.Created,
                        ImageUploadResponse(
                            message = "Images uploaded successfully",
                            images = uploadedImages.map { 
                                ImageInfo(
                                    url = it.url,
                                    fileName = it.fileName,
                                    size = it.size.toLong(),
                                    contentType = it.contentType
                                )
                            }
                        )
                    )
                } catch (e: com.codewithfk.services.ImageUploadException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Upload failed"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Upload failed"))
                }
            }
            
            // Upload user profile image
            post("/users/profile") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                
                try {
                    val multipartData = call.receiveMultipart()
                    var uploadedImage: com.codewithfk.services.ImageUploadResult? = null
                    
                    multipartData.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                val fileName = part.originalFileName ?: "image"
                                val contentType = part.contentType?.toString() ?: "application/octet-stream"
                                
                                // Validate
                                val validation = imageService.validateImageFile(
                                    fileName = fileName,
                                    contentType = contentType,
                                    size = part.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
                                )
                                
                                if (!validation.isValid) {
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        ValidationErrorResponse(
                                            error = "Validation failed",
                                            details = validation.errors
                                        )
                                    )
                                    return@forEachPart
                                }
                                
                                val fileBytes = part.streamProvider().readBytes()
                                uploadedImage = imageService.uploadImage(
                                    fileBytes = fileBytes,
                                    fileName = fileName,
                                    contentType = contentType,
                                    userId = userId
                                )
                            }
                            else -> {}
                        }
                        part.dispose()
                    }
                    
                    if (uploadedImage == null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("No image file provided"))
                        return@post
                    }
                    
                    call.respond(
                        HttpStatusCode.Created,
                        SingleImageUploadResponse(
                            message = "Profile image uploaded successfully",
                            image = ImageInfo(
                                url = uploadedImage.url,
                                fileName = uploadedImage.fileName,
                                size = uploadedImage.size.toLong(),
                                contentType = uploadedImage.contentType
                            )
                        )
                    )
                } catch (e: com.codewithfk.services.ImageUploadException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Upload failed"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Upload failed"))
                }
            }
            
            // Delete image
            delete("/listings/{listingId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                val role = principal?.payload?.getClaim("role")?.asString()
                val listingId = call.parameters["listingId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@delete
                }
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@delete
                }
                
                val imageUrl = call.request.queryParameters["url"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Image URL required"))
                    return@delete
                }
                
                // Verify listing ownership
                val listing = listingService.getListingById(listingId)
                if (listing == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@delete
                }
                
                if (listing.vendorId != userId && role != "ADMIN") {
                    call.respond(HttpStatusCode.Forbidden)
                    return@delete
                }
                
                val deleted = imageService.deleteImage(imageUrl, listingId)
                if (deleted) {
                    call.respond(HttpStatusCode.OK, MessageResponse("Image deleted successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Image not found"))
                }
            }
            
            // Get listing images
            get("/listings/{listingId}") {
                val listingId = call.parameters["listingId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                
                val images = imageService.getListingImages(listingId)
                call.respond(ImagesListResponse(images = images))
            }
        }
    }
}
