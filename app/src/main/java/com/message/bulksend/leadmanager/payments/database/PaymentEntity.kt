package com.message.bulksend.leadmanager.payments.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.message.bulksend.leadmanager.database.entities.LeadEntity

/**
 * Payment type - Received or Given
 */
enum class PaymentType {
    RECEIVED,  // Money received from lead
    GIVEN      // Money given to lead
}

/**
 * Payment Entity for storing payment records
 */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = LeadEntity::class,
            parentColumns = ["id"],
            childColumns = ["leadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["leadId"])]
)
data class PaymentEntity(
    @PrimaryKey
    val id: String,
    val leadId: String,
    val amount: Double,
    val paymentType: PaymentType,
    val description: String = "",
    val timestamp: Long,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Invoice status
 */
enum class InvoiceStatus {
    DRAFT,
    SENT,
    PAID,
    CANCELLED
}

/**
 * Invoice Entity for storing invoices
 */
@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(
            entity = LeadEntity::class,
            parentColumns = ["id"],
            childColumns = ["leadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["leadId"])]
)
data class InvoiceEntity(
    @PrimaryKey
    val id: String,
    val leadId: String,
    val invoiceNumber: String,
    val amount: Double,
    val tax: Double = 0.0,
    val totalAmount: Double,
    val status: InvoiceStatus = InvoiceStatus.DRAFT,
    val addressTo: String = "",
    val addressFrom: String = "",
    val comments: String = "",
    val items: String = "", // JSON array of items
    val timestamp: Long,
    val dueDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
