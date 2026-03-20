package com.message.bulksend.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a simple key-value pair for storing app settings in the database.
 * The 'key' is the primary key to ensure uniqueness.
 */
@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String
)
