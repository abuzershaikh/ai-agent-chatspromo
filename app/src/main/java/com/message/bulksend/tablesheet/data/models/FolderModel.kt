package com.message.bulksend.tablesheet.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderModel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val colorHex: String = "#1976D2" // Store color as hex string
)