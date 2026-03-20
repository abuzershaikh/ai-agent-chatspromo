package com.message.bulksend.autorespond.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["catalogueId", "sortOrder"]),
        Index(value = ["catalogueId", "isVisible"])
    ]
)
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val catalogueId: Long = 0,        // 0 = standalone, >0 = linked to Catalogue
    val name: String,
    val description: String = "",
    val price: Double = 0.0,
    val currency: String = "INR",
    val category: String = "",
    val link: String = "",
    val thumbnailPath: String = "",
    val isVisible: Boolean = true,
    val mediaPaths: String = "[]",    // JSON array of MediaItem {path, isVideo}
    val customFields: String = "[]",  // JSON array of CustomField {name, type, value}
    val sortOrder: Int = 0,           // Display order inside catalogue
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
