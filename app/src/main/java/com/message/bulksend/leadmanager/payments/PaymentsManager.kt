package com.message.bulksend.leadmanager.payments

import android.content.Context
import com.message.bulksend.leadmanager.database.LeadManagerDatabase
import com.message.bulksend.leadmanager.payments.database.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class PaymentsManager(context: Context) {
    private val database = LeadManagerDatabase.getDatabase(context)
    private val paymentDao = database.paymentDao()
    
    // Payment Operations
    fun getPaymentsForLead(leadId: String): Flow<List<PaymentEntity>> {
        return paymentDao.getPaymentsForLead(leadId)
    }
    
    suspend fun getPaymentsForLeadList(leadId: String): List<PaymentEntity> {
        return paymentDao.getPaymentsForLeadList(leadId)
    }
    
    suspend fun getTotalReceived(leadId: String): Double {
        return paymentDao.getTotalReceived(leadId) ?: 0.0
    }
    
    suspend fun getTotalGiven(leadId: String): Double {
        return paymentDao.getTotalGiven(leadId) ?: 0.0
    }
    
    suspend fun addPayment(
        leadId: String,
        amount: Double,
        type: PaymentType,
        description: String = ""
    ) {
        val payment = PaymentEntity(
            id = UUID.randomUUID().toString(),
            leadId = leadId,
            amount = amount,
            paymentType = type,
            description = description,
            timestamp = System.currentTimeMillis()
        )
        paymentDao.insertPayment(payment)
    }
    
    suspend fun deletePayment(paymentId: String) {
        paymentDao.deletePaymentById(paymentId)
    }
    
    // Invoice Operations
    fun getInvoicesForLead(leadId: String): Flow<List<InvoiceEntity>> {
        return paymentDao.getInvoicesForLead(leadId)
    }
    
    suspend fun getInvoicesForLeadList(leadId: String): List<InvoiceEntity> {
        return paymentDao.getInvoicesForLeadList(leadId)
    }
    
    suspend fun getNextInvoiceNumber(leadId: String): String {
        val count = paymentDao.getInvoiceCount(leadId)
        return "INV-${count + 1}"
    }
    
    suspend fun addInvoice(invoice: InvoiceEntity) {
        paymentDao.insertInvoice(invoice)
    }
    
    suspend fun updateInvoice(invoice: InvoiceEntity) {
        paymentDao.updateInvoice(invoice)
    }
    
    suspend fun deleteInvoice(invoiceId: String) {
        paymentDao.deleteInvoiceById(invoiceId)
    }
}
