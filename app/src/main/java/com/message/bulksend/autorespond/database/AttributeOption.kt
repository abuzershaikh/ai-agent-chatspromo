package com.message.bulksend.autorespond.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attribute_options",
    foreignKeys = [
        ForeignKey(
            entity = AttributeGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("groupId"),
        Index(value = ["groupId", "displayOrder"]),
        Index(value = ["groupId", "value"], unique = true)
    ]
)
data class AttributeOption(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: Long,
    val value: String,          // e.g. "S", "M", "L", "Red", "Blue"
    val displayOrder: Int = 0,
    val hexColor: String = ""   // For COLOR type groups
)
