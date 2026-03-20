package com.message.bulksend.tools.invoicemaker.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "tool_invoices")
data class InvoiceEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val invoiceNumber: String,
    val invoiceDate: Long,
    val dueDate: Long? = null,
    
    // Business Info
    val businessName: String,
    val businessAddress: String,
    val businessPhone: String,
    val businessEmail: String,
    val businessLogoUri: String? = null,
    val taxNumber: String,
    
    // Client Info
    val clientName: String,
    val clientAddress: String,
    val clientPhone: String,
    val clientEmail: String,
    
    // Items (JSON string)
    val itemsJson: String,
    
    // Amounts
    val subtotal: Double,
    val taxRate: Double,
    val taxAmount: Double,
    val discount: Double,
    val totalAmount: Double,
    
    // Additional
    val notes: String,
    val bankDetails: String,
    val currencyCode: String,
    val currencySymbol: String,
    
    // Status
    val status: String = "CREATED", // CREATED, SENT, PAID, CANCELLED
    val pdfPath: String? = null,
    val pngPath: String? = null,
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
