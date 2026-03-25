package com.message.bulksend.tablesheet.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cell_search_index",
    indices = [
        Index(value = ["rowId", "columnId"], unique = true),
        Index("tableId"),
        Index("normalizedValue"),
        Index(value = ["tableId", "normalizedValue"])
    ]
)
data class CellSearchIndexModel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tableId: Long,
    val rowId: Long,
    val columnId: Long,
    val normalizedValue: String,
    val updatedAt: Long = System.currentTimeMillis()
)
