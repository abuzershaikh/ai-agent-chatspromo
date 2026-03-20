package com.message.bulksend.tablesheet.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rows",
    foreignKeys = [
        ForeignKey(
            entity = TableModel::class,
            parentColumns = ["id"],
            childColumns = ["tableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tableId")]
)
data class RowModel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tableId: Long,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
