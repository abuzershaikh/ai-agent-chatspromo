package com.message.bulksend.tools.invoicemaker.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity)
    
    @Update
    suspend fun updateInvoice(invoice: InvoiceEntity)
    
    @Delete
    suspend fun deleteInvoice(invoice: InvoiceEntity)
    
    @Query("DELETE FROM tool_invoices WHERE id = :invoiceId")
    suspend fun deleteInvoiceById(invoiceId: String)
    
    @Query("SELECT * FROM tool_invoices ORDER BY createdAt DESC")
    fun getAllInvoices(): Flow<List<InvoiceEntity>>
    
    @Query("SELECT * FROM tool_invoices WHERE id = :invoiceId")
    suspend fun getInvoiceById(invoiceId: String): InvoiceEntity?
    
    @Query("SELECT * FROM tool_invoices WHERE invoiceNumber = :invoiceNumber")
    suspend fun getInvoiceByNumber(invoiceNumber: String): InvoiceEntity?
    
    @Query("SELECT COUNT(*) FROM tool_invoices")
    fun getTotalInvoiceCount(): Flow<Int>
    
    @Query("SELECT COALESCE(SUM(totalAmount), 0) FROM tool_invoices")
    fun getTotalAmount(): Flow<Double>
    
    @Query("SELECT COALESCE(SUM(totalAmount), 0) FROM tool_invoices WHERE invoiceDate >= :startOfMonth")
    fun getThisMonthAmount(startOfMonth: Long): Flow<Double>
    
    @Query("SELECT COUNT(*) FROM tool_invoices WHERE status = :status")
    fun getInvoiceCountByStatus(status: String): Flow<Int>
    
    @Query("SELECT * FROM tool_invoices WHERE clientName LIKE '%' || :query || '%' OR invoiceNumber LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchInvoices(query: String): Flow<List<InvoiceEntity>>
    
    @Query("UPDATE tool_invoices SET status = :status, updatedAt = :updatedAt WHERE id = :invoiceId")
    suspend fun updateInvoiceStatus(invoiceId: String, status: String, updatedAt: Long = System.currentTimeMillis())
}
