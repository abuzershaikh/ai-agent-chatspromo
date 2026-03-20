package com.message.bulksend.autorespond.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attribute_groups",
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
        Index(value = ["productId", "displayOrder"])
    ]
)
data class AttributeGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val name: String,           // e.g. "Size", "Color", "Weight"
    val type: String = "SELECT", // SELECT, COLOR, NUMBER
    val displayOrder: Int = 0
)
