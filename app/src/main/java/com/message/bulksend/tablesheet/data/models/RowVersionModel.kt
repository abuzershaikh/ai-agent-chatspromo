package com.message.bulksend.tablesheet.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "row_versions",
    indices = [
        Index("transactionId"),
        Index("tableId"),
        Index("rowId"),
        Index(value = ["rowId", "columnId"])
    ]
)
data class RowVersionModel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionId: String,
    val tableId: Long,
    val rowId: Long,
    val columnId: Long,
    val previousValue: String,
    val newValue: String,
    val source: String,
    val changedAt: Long = System.currentTimeMillis()
)
