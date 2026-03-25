package com.message.bulksend.tablesheet.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "columns",
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
data class ColumnModel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tableId: Long,
    val name: String,
    val type: String = "STRING",
    val orderIndex: Int = 0,
    val width: Float = 1f,
    val selectOptions: String? = null // JSON string for SELECT type options
)

// Column Types
object ColumnType {
    const val STRING = "STRING"
    const val INTEGER = "INTEGER"
    const val DECIMAL = "DECIMAL"
    const val AMOUNT = "AMOUNT"
    const val DATE = "DATEONLY"
    const val DATETIME = "DATETIME"
    const val TIME = "TIME"
    const val CHECKBOX = "CHECKBOX"
    const val SELECT = "SELECT"
    const val MULTI_SELECT = "MULTI_SELECT"
    const val PHONE = "PHONE"
    const val EMAIL = "EMAIL"
    const val IMAGE = "IMAGE"
    const val URL = "URL"
    const val MAP = "MAP"
    const val MULTILINE = "MULTILINE"
    const val FILE = "FILE"
    const val JSON = "JSON"
    const val FORMULA = "FORMULA"
    const val AUDIO = "AUDIO"
    const val DRAW = "DRAW"
    const val PRIORITY = "PRIORITY"
}
