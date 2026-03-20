package com.message.bulksend.product

import android.content.Context
import android.net.Uri
import com.message.bulksend.autorespond.database.MessageDatabase
import com.message.bulksend.autorespond.database.Product
import com.message.bulksend.autorespond.database.ProductDao
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

class ProductRepository(context: Context) {
    private val appContext = context.applicationContext
    private val productDao: ProductDao = MessageDatabase.getDatabase(appContext).productDao()
    
    fun getAllProducts(): Flow<List<Product>> = productDao.getAllProducts()
    fun getVisibleProducts(): Flow<List<Product>> = productDao.getVisibleProducts()
    
    suspend fun getProductById(id: Long): Product? = productDao.getProductById(id)
    suspend fun searchProducts(query: String): List<Product> = productDao.searchProducts(query)
    suspend fun getAllCategories(): List<String> = productDao.getAllCategories()
    suspend fun getProductCount(): Int = productDao.getProductCount()
    
    // Synchronous version for AI Agent
    fun getAllProductsSync(): List<Product> {
        return try {
            productDao.getAllProductsSync()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun addProduct(product: Product): Long {
        return productDao.insertProduct(product)
    }
    
    suspend fun updateProduct(product: Product) {
        productDao.updateProduct(product.copy(updatedAt = System.currentTimeMillis()))
    }
    
    suspend fun deleteProduct(product: Product) {
        // Delete associated media files
        val mediaItems = MediaItem.listFromJson(product.mediaPaths)
        mediaItems.forEach { item ->
            try { File(item.path).delete() } catch (_: Exception) {}
        }
        if (product.thumbnailPath.isNotBlank()) {
            try { File(product.thumbnailPath).delete() } catch (_: Exception) {}
        }
        productDao.deleteProduct(product)
    }
    
    /**
     * Copy a media file from URI to internal storage.
     * Returns the internal storage path.
     */
    fun saveMediaToStorage(uri: Uri, isVideo: Boolean = false, isPdf: Boolean = false, isAudio: Boolean = false): String? {
        return try {
            val ext = when {
                isPdf -> "pdf"
                isAudio -> {
                    // Try to get actual extension from URI
                    val mimeType = appContext.contentResolver.getType(uri)
                    when {
                        mimeType?.contains("mp3") == true -> "mp3"
                        mimeType?.contains("m4a") == true -> "m4a"
                        mimeType?.contains("wav") == true -> "wav"
                        mimeType?.contains("ogg") == true -> "ogg"
                        else -> "mp3" // default
                    }
                }
                isVideo -> "mp4"
                else -> "jpg"
            }
            val dir = File(appContext.filesDir, "product_media")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${UUID.randomUUID()}.$ext")
            
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Convert a full product into a text description suitable for AI consumption.
     */
    fun getProductAsText(product: Product): String {
        val sb = StringBuilder()
        sb.appendLine("Product: ${product.name}")
        if (product.price > 0) sb.appendLine("Price: ${product.currency} ${product.price}")
        if (product.category.isNotBlank()) sb.appendLine("Category: ${product.category}")
        if (product.description.isNotBlank()) sb.appendLine("Description: ${product.description}")
        if (product.link.isNotBlank()) sb.appendLine("Link: ${product.link}")
        
        val customFields = CustomField.listFromJson(product.customFields)
        if (customFields.isNotEmpty()) {
            customFields.forEach { field ->
                if (field.value.isNotBlank()) {
                    sb.appendLine("${field.name}: ${field.value}")
                }
            }
        }
        
        val media = MediaItem.listFromJson(product.mediaPaths)
        if (media.isNotEmpty()) {
            val imageCount = media.count { !it.isVideo && !it.isPdf && !it.isAudio }
            val videoCount = media.count { it.isVideo }
            val pdfCount = media.count { it.isPdf }
            val audioCount = media.count { it.isAudio }
            if (imageCount > 0) sb.appendLine("Images: $imageCount available")
            if (videoCount > 0) sb.appendLine("Videos: $videoCount available")
            if (pdfCount > 0) sb.appendLine("PDFs: $pdfCount available")
            if (audioCount > 0) sb.appendLine("Audio files: $audioCount available")
        }
        
        return sb.toString()
    }
}
