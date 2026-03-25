package com.message.bulksend.tablesheet.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tables")
data class TableModel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val rowCount: Int = 0,
    val columnCount: Int = 0,
    val frozenColumnCount: Int = 0,
    val tags: String? = null, // Comma separated tags
    val isFavorite: Boolean = false,
    val folderId: Long? = null // Folder ID for organization
)
