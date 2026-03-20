package com.message.bulksend.autorespond.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_variants",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("productId"),
        Index(value = ["productId", "isAvailable"]),
        Index(value = ["productId", "optionIds"], unique = true)
    ]
)
data class ProductVariant(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val price: Double = 0.0,
    val stock: Int = -1,        // -1 = unlimited
    val sku: String = "",
    val imageOverride: String = "", // Optional image specific to this variant
    val isAvailable: Boolean = true,
    val optionIds: String = "[]"  // JSON array of option IDs for this variant combo
)
