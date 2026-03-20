package com.message.bulksend.leadmanager.payments.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    // Payment Operations
    @Query("SELECT * FROM payments WHERE leadId = :leadId ORDER BY timestamp DESC")
    fun getPaymentsForLead(leadId: String): Flow<List<PaymentEntity>>
    
    @Query("SELECT * FROM payments WHERE leadId = :leadId ORDER BY timestamp DESC")
    suspend fun getPaymentsForLeadList(leadId: String): List<PaymentEntity>
    
    @Query("SELECT SUM(amount) FROM payments WHERE leadId = :leadId AND paymentType = 'RECEIVED'")
    suspend fun getTotalReceived(leadId: String): Double?
    
    @Query("SELECT SUM(amount) FROM payments WHERE leadId = :leadId AND paymentType = 'GIVEN'")
    suspend fun getTotalGiven(leadId: String): Double?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity)
    
    @Delete
    suspend fun deletePayment(payment: PaymentEntity)
    
    @Query("DELETE FROM payments WHERE id = :paymentId")
    suspend fun deletePaymentById(paymentId: String)
    
    // Invoice Operations
    @Query("SELECT * FROM invoices WHERE leadId = :leadId ORDER BY timestamp DESC")
    fun getInvoicesForLead(leadId: String): Flow<List<InvoiceEntity>>
    
    @Query("SELECT * FROM invoices WHERE leadId = :leadId ORDER BY timestamp DESC")
    suspend fun getInvoicesForLeadList(leadId: String): List<InvoiceEntity>
    
    @Query("SELECT * FROM invoices WHERE id = :invoiceId")
    suspend fun getInvoiceById(invoiceId: String): InvoiceEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity)
    
    @Update
    suspend fun updateInvoice(invoice: InvoiceEntity)
    
    @Query("DELETE FROM invoices WHERE id = :invoiceId")
    suspend fun deleteInvoiceById(invoiceId: String)
    
    @Query("SELECT COUNT(*) FROM invoices WHERE leadId = :leadId")
    suspend fun getInvoiceCount(leadId: String): Int
    
    @Query("SELECT * FROM payments ORDER BY timestamp DESC")
    suspend fun getAllPaymentsList(): List<PaymentEntity>
    
    @Query("SELECT * FROM invoices ORDER BY timestamp DESC")
    suspend fun getAllInvoicesList(): List<InvoiceEntity>
    
    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getPaymentById(id: String): PaymentEntity?
    
    @Query("SELECT COUNT(*) FROM payments")
    suspend fun getPaymentsCount(): Int
    
    @Query("SELECT COUNT(*) FROM invoices")
    suspend fun getInvoicesCount(): Int
}
