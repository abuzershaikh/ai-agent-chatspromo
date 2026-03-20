package com.message.bulksend.tablesheet.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cells",
    foreignKeys = [
        ForeignKey(
            entity = RowModel::class,
            parentColumns = ["id"],
            childColumns = ["rowId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ColumnModel::class,
            parentColumns = ["id"],
            childColumns = ["columnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("rowId"), Index("columnId"), Index(value = ["rowId", "columnId"], unique = true)]
)
data class CellModel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rowId: Long,
    val columnId: Long,
    val value: String = ""
)
