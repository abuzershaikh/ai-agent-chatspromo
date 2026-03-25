package com.message.bulksend.tablesheet.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "formula_dependencies",
    foreignKeys = [
        ForeignKey(
            entity = CellModel::class,
            parentColumns = ["id"],
            childColumns = ["dependentCellId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("tableId"),
        Index("dependentCellId"),
        Index(value = ["sourceRowId", "sourceColumnId"])
    ]
)
data class FormulaDependencyModel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tableId: Long,
    val dependentCellId: Long,
    val sourceRowId: Long,
    val sourceColumnId: Long,
    val sourceAddress: String,
    val createdAt: Long = System.currentTimeMillis()
)
