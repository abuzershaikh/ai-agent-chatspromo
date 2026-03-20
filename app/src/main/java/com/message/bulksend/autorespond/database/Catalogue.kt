package com.message.bulksend.autorespond.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalogues")
data class Catalogue(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val themeColor: String = "#8B5CF6",     // Hex color for UI
    val layoutType: String = "GRID",         // GRID or LIST
    val shareToken: String = "",             // For future sharing
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
