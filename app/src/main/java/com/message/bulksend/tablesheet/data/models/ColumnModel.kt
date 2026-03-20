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
    val type: String = "STRING", // STRING, INTEGER, AMOUNT, DATE, CHECKBOX, SELECT, PHONE, EMAIL, IMAGE, PRIORITY
    val orderIndex: Int = 0,
    val width: Float = 1f,
    val selectOptions: String? = null // JSON string for SELECT type options
)

// Column Types
object ColumnType {
    const val STRING = "STRING"
    const val INTEGER = "INTEGER"
    const val AMOUNT = "AMOUNT"
    const val DATE = "DATEONLY"
    const val TIME = "TIME"
    const val CHECKBOX = "CHECKBOX"
    const val SELECT = "SELECT"
    const val PHONE = "PHONE"
    const val EMAIL = "EMAIL"
    const val IMAGE = "IMAGE"
    const val PRIORITY = "PRIORITY"
}
