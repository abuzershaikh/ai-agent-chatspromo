package com.message.bulksend.autorespond.ai.product

import android.content.Context
import android.net.Uri
import android.util.Log
import com.message.bulksend.autorespond.ai.document.AIAgentDocumentManager
import com.message.bulksend.autorespond.database.Product
import com.message.bulksend.autorespond.documentreply.DocumentFile
import com.message.bulksend.autorespond.documentreply.DocumentType
import com.message.bulksend.product.MediaItem
import com.message.bulksend.product.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * AI Agent Product Manager
 * Gives AI Agent full power to send product catalogues with all media
 * 
 * Features:
 * - Send complete product catalogue (all media + details)
 * - Send product details as text message
 * - Send all product media (images, videos, PDFs, audio)
 * - Search products by name/category
 * - Get product information
 */
class AIAgentProductManager(private val context: Context) {
    
    companion object {
        const val TAG = "AIAgentProductManager"
    }
    
    private val productRepository = ProductRepository(context)
    private val documentManager = AIAgentDocumentManager(context)
    
    /**
     * Send complete product catalogue to user
     * Includes: Product details text + All media files
     */
    suspend fun sendProductCatalogue(
        phoneNumber: String,
        userName: String,
        productId: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📦 Sending product catalogue for product ID: $productId")
            
            // Get product with error handling
            val product = try {
                productRepository.getProductById(productId)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error fetching product: ${e.message}")
                null
            }
            
            if (product == null) {
                Log.e(TAG, "❌ Product not found: $productId")
                return@withContext false
            }
            
            Log.d(TAG, "✅ Product found: ${product.name}")
            
            // Step 1: Get product details text
            val productDetailsText = try {
                productRepository.getProductAsText(product)
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ Error getting product text: ${e.message}")
                "Product: ${product.name}" // Fallback text
            }
            Log.d(TAG, "📝 Product details:\n$productDetailsText")
            
            // Step 2: Send all media files (text will be sent via AI reply)
            val mediaItems = try {
                MediaItem.listFromJson(product.mediaPaths)
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ Error parsing media paths: ${e.message}")
                emptyList()
            }
            
            Log.d(TAG, "📁 Product has ${mediaItems.size} media files")
            
            if (mediaItems.isEmpty()) {
                Log.d(TAG, "⚠️ No media files to send, only text details available")
                // Return true because text details are already in AI reply
                return@withContext true
            }
            
            // Convert MediaItems to DocumentFiles for sending
            val documentFiles = mediaItems.mapNotNull { mediaItem ->
                try {
                    val documentType = when {
                        mediaItem.isPdf -> DocumentType.PDF
                        mediaItem.isAudio -> DocumentType.AUDIO
                        mediaItem.isVideo -> DocumentType.VIDEO
                        else -> DocumentType.IMAGE
                    }
                    
                    // Check if file exists
                    val resolvedPath = resolveMediaPathForSend(mediaItem.path)
                    if (resolvedPath == null) {
                        Log.w(TAG, "Media not accessible: ${mediaItem.path}")
                        return@mapNotNull null
                    }
                    
                    DocumentFile(
                        originalName = resolveDisplayName(resolvedPath),
                        savedPath = resolvedPath,
                        documentType = documentType,
                        fileSize = resolveSizeBytes(resolvedPath)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing media item: ${e.message}")
                    null
                }
            }
            
            if (documentFiles.isEmpty()) {
                Log.e(TAG, "❌ No valid media files found")
                return@withContext false
            }
            
            Log.d(TAG, "📤 Sending ${documentFiles.size} media files")
            
            // Send all media files with error handling
            val success = try {
                documentManager.sendMultipleDocuments(
                    phoneNumber = phoneNumber,
                    userName = userName,
                    documents = documentFiles
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error sending documents: ${e.message}")
                false
            }
            
            if (success) {
                Log.d(TAG, "✅ Product catalogue sent successfully")
            } else {
                Log.e(TAG, "❌ Failed to send product catalogue")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending product catalogue: ${e.message}", e)
            false
        }
    }
    
    /**
     * Send product details as formatted text
     * Returns formatted product information
     */
    suspend fun getProductDetailsText(productId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val product = productRepository.getProductById(productId)
            if (product == null) {
                Log.e(TAG, "❌ Product not found: $productId")
                return@withContext null
            }
            
            productRepository.getProductAsText(product)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting product details: ${e.message}")
            null
        }
    }
    
    /**
     * Send only product media (no text)
     */
    suspend fun sendProductMedia(
        phoneNumber: String,
        userName: String,
        productId: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val product = productRepository.getProductById(productId)
            if (product == null) {
                Log.e(TAG, "❌ Product not found: $productId")
                return@withContext false
            }
            
            val mediaItems = MediaItem.listFromJson(product.mediaPaths)
            if (mediaItems.isEmpty()) {
                Log.d(TAG, "⚠️ No media files to send")
                return@withContext false
            }
            
            val documentFiles = mediaItems.mapNotNull { mediaItem ->
                val documentType = when {
                    mediaItem.isPdf -> DocumentType.PDF
                    mediaItem.isAudio -> DocumentType.AUDIO
                    mediaItem.isVideo -> DocumentType.VIDEO
                    else -> DocumentType.IMAGE
                }
                
                val resolvedPath = resolveMediaPathForSend(mediaItem.path) ?: return@mapNotNull null
                
                DocumentFile(
                    originalName = resolveDisplayName(resolvedPath),
                    savedPath = resolvedPath,
                    documentType = documentType,
                    fileSize = resolveSizeBytes(resolvedPath)
                )
            }
            
            documentManager.sendMultipleDocuments(phoneNumber, userName, documentFiles)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending product media: ${e.message}")
            false
        }
    }
    
    /**
     * Send specific media type from product
     */
    suspend fun sendProductMediaByType(
        phoneNumber: String,
        userName: String,
        productId: Long,
        mediaType: DocumentType
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val product = productRepository.getProductById(productId)
            if (product == null) return@withContext false
            
            val mediaItems = MediaItem.listFromJson(product.mediaPaths)
            val filteredMedia = mediaItems.filter { mediaItem ->
                when (mediaType) {
                    DocumentType.PDF -> mediaItem.isPdf
                    DocumentType.AUDIO -> mediaItem.isAudio
                    DocumentType.VIDEO -> mediaItem.isVideo
                    DocumentType.IMAGE -> !mediaItem.isPdf && !mediaItem.isAudio && !mediaItem.isVideo
                }
            }
            
            if (filteredMedia.isEmpty()) {
                Log.d(TAG, "⚠️ No ${mediaType.name} files found")
                return@withContext false
            }
            
            val documentFiles = filteredMedia.mapNotNull { mediaItem ->
                val resolvedPath = resolveMediaPathForSend(mediaItem.path) ?: return@mapNotNull null
                
                DocumentFile(
                    originalName = resolveDisplayName(resolvedPath),
                    savedPath = resolvedPath,
                    documentType = mediaType,
                    fileSize = resolveSizeBytes(resolvedPath)
                )
            }
            
            documentManager.sendMultipleDocuments(phoneNumber, userName, documentFiles)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending product media by type: ${e.message}")
            false
        }
    }
    
    /**
     * Search products by name or category
     */
    suspend fun searchProducts(query: String): List<Product> = withContext(Dispatchers.IO) {
        try {
            val allProducts = productRepository.getAllProductsSync()
            allProducts.filter { product ->
                product.name.contains(query, ignoreCase = true) ||
                product.category.contains(query, ignoreCase = true) ||
                product.description.contains(query, ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error searching products: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get product by name (exact or partial match)
     */
    suspend fun getProductByName(name: String): Product? = withContext(Dispatchers.IO) {
        try {
            val allProducts = productRepository.getAllProductsSync()
            
            // Try exact match first
            var product = allProducts.find { it.name.equals(name, ignoreCase = true) }
            
            // If not found, try partial match
            if (product == null) {
                product = allProducts.find { it.name.contains(name, ignoreCase = true) }
            }
            
            product
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting product by name: ${e.message}")
            null
        }
    }
    
    /**
     * Get all products in a category
     */
    suspend fun getProductsByCategory(category: String): List<Product> = withContext(Dispatchers.IO) {
        try {
            val allProducts = productRepository.getAllProductsSync()
            allProducts.filter { it.category.equals(category, ignoreCase = true) }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting products by category: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get all available products
     */
    suspend fun getAllProducts(): List<Product> = withContext(Dispatchers.IO) {
        try {
            productRepository.getAllProductsSync()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting all products: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get product media count
     */
    suspend fun getProductMediaCount(productId: Long): MediaCount? = withContext(Dispatchers.IO) {
        try {
            val product = productRepository.getProductById(productId) ?: return@withContext null
            val mediaItems = MediaItem.listFromJson(product.mediaPaths)
            
            MediaCount(
                images = mediaItems.count { !it.isPdf && !it.isAudio && !it.isVideo },
                videos = mediaItems.count { it.isVideo },
                pdfs = mediaItems.count { it.isPdf },
                audios = mediaItems.count { it.isAudio },
                total = mediaItems.size
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting media count: ${e.message}")
            null
        }
    }
    
    /**
     * Check if product has media
     */
    suspend fun hasMedia(productId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val product = productRepository.getProductById(productId) ?: return@withContext false
            val mediaItems = MediaItem.listFromJson(product.mediaPaths)
            mediaItems.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get product summary for AI context
     */
    suspend fun getProductSummary(productId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val product = productRepository.getProductById(productId) ?: return@withContext null
            val mediaCount = getProductMediaCount(productId)
            
            buildString {
                append("${product.name}")
                if (product.price > 0) {
                    append(" - ${product.currency} ${product.price}")
                }
                if (mediaCount != null && mediaCount.total > 0) {
                    append(" (${mediaCount.total} media files)")
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Send product catalogue with delay between media
     * Useful for large catalogues
     */
    suspend fun sendProductCatalogueWithDelay(
        phoneNumber: String,
        userName: String,
        productId: Long,
        delayBetweenMedia: Long = 3000 // 3 seconds default
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val product = productRepository.getProductById(productId) ?: return@withContext false
            val mediaItems = MediaItem.listFromJson(product.mediaPaths)
            
            if (mediaItems.isEmpty()) return@withContext true
            
            // Send media one by one with delay
            for ((index, mediaItem) in mediaItems.withIndex()) {
                val documentType = when {
                    mediaItem.isPdf -> DocumentType.PDF
                    mediaItem.isAudio -> DocumentType.AUDIO
                    mediaItem.isVideo -> DocumentType.VIDEO
                    else -> DocumentType.IMAGE
                }
                
                val resolvedPath = resolveMediaPathForSend(mediaItem.path) ?: continue
                
                val success = when (documentType) {
                    DocumentType.IMAGE -> documentManager.sendImage(phoneNumber, userName, resolvedPath)
                    DocumentType.VIDEO -> documentManager.sendVideo(phoneNumber, userName, resolvedPath)
                    DocumentType.PDF -> documentManager.sendPDF(phoneNumber, userName, resolvedPath)
                    DocumentType.AUDIO -> documentManager.sendAudio(phoneNumber, userName, resolvedPath)
                }
                
                if (!success) {
                    Log.w(TAG, "⚠️ Failed to send media ${index + 1}/${mediaItems.size}")
                }
                
                // Delay before next media (except for last one)
                if (index < mediaItems.size - 1) {
                    delay(delayBetweenMedia)
                }
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending catalogue with delay: ${e.message}")
            false
        }
    }
    private fun resolveMediaPathForSend(rawPath: String): String? {
        val trimmed = rawPath.trim()
        if (trimmed.isBlank()) return null

        return when {
            trimmed.startsWith("content://", ignoreCase = true) -> trimmed
            trimmed.startsWith("file://", ignoreCase = true) -> {
                val filePath = Uri.parse(trimmed).path ?: return null
                val file = java.io.File(filePath)
                if (file.exists()) file.absolutePath else null
            }
            else -> {
                val file = java.io.File(trimmed)
                if (file.exists()) file.absolutePath else null
            }
        }
    }

    private fun resolveDisplayName(path: String): String {
        return if (path.startsWith("content://", ignoreCase = true)) {
            Uri.parse(path).lastPathSegment?.takeIf { it.isNotBlank() } ?: "media"
        } else {
            java.io.File(path).name.ifBlank { "media" }
        }
    }

    private fun resolveSizeBytes(path: String): Long {
        return if (path.startsWith("content://", ignoreCase = true)) 0L else java.io.File(path).length()
    }
}

/**
 * Media count data class
 */
data class MediaCount(
    val images: Int,
    val videos: Int,
    val pdfs: Int,
    val audios: Int,
    val total: Int
) {
    override fun toString(): String {
        val parts = mutableListOf<String>()
        if (images > 0) parts.add("$images image${if (images > 1) "s" else ""}")
        if (videos > 0) parts.add("$videos video${if (videos > 1) "s" else ""}")
        if (pdfs > 0) parts.add("$pdfs PDF${if (pdfs > 1) "s" else ""}")
        if (audios > 0) parts.add("$audios audio${if (audios > 1) "s" else ""}")
        return parts.joinToString(", ")
    }
}



