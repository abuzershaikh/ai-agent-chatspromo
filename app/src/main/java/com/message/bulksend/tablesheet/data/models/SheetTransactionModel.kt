package com.message.bulksend.tablesheet.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sheet_transactions",
    indices = [
        Index("tableId"),
        Index("status"),
        Index("createdAt")
    ]
)
data class SheetTransactionModel(
    @PrimaryKey
    val transactionId: String,
    val tableId: Long,
    val action: String,
    val status: String = STATUS_RUNNING,
    val metadata: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    companion object {
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_COMMITTED = "COMMITTED"
        const val STATUS_FAILED = "FAILED"
    }
}
