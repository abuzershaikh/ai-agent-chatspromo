package com.message.bulksend.tools.invoicemaker.database

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.tools.invoicemaker.InvoiceDataTool
import com.message.bulksend.tools.invoicemaker.InvoiceItemData
import com.message.bulksend.tools.invoicemaker.BusinessInfoTool
import com.message.bulksend.tools.invoicemaker.ClientInfoTool
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class InvoiceRepository(context: Context) {
    
    private val database = InvoiceDatabase.getDatabase(context)
    private val invoiceDao = database.invoiceDao()
    private val gson = Gson()
    
    val allInvoices: Flow<List<InvoiceEntity>> = invoiceDao.getAllInvoices()
    val totalInvoiceCount: Flow<Int> = invoiceDao.getTotalInvoiceCount()
    val totalAmount: Flow<Double> = invoiceDao.getTotalAmount()
    
    fun getThisMonthAmount(): Flow<Double> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return invoiceDao.getThisMonthAmount(calendar.timeInMillis)
    }
    
    fun getPendingCount(): Flow<Int> = invoiceDao.getInvoiceCountByStatus("CREATED")
    
    suspend fun saveInvoice(invoiceData: InvoiceDataTool, pdfPath: String? = null, pngPath: String? = null) {
        val entity = InvoiceEntity(
            id = invoiceData.id,
            invoiceNumber = invoiceData.invoiceNumber,
            invoiceDate = invoiceData.invoiceDate,
            dueDate = invoiceData.dueDate,
            businessName = invoiceData.businessInfo.businessName,
            businessAddress = invoiceData.businessInfo.address,
            businessPhone = invoiceData.businessInfo.phone,
            businessEmail = invoiceData.businessInfo.email,
            businessLogoUri = invoiceData.businessInfo.logoUri,
            taxNumber = invoiceData.businessInfo.taxNumber,
            clientName = invoiceData.clientInfo.name,
            clientAddress = invoiceData.clientInfo.address,
            clientPhone = invoiceData.clientInfo.phone,
            clientEmail = invoiceData.clientInfo.email,
            itemsJson = gson.toJson(invoiceData.items),
            subtotal = invoiceData.subtotal,
            taxRate = invoiceData.taxRate,
            taxAmount = invoiceData.taxAmount,
            discount = invoiceData.discount,
            totalAmount = invoiceData.totalAmount,
            notes = invoiceData.notes,
            bankDetails = invoiceData.bankDetails,
            currencyCode = invoiceData.currencyCode,
            currencySymbol = invoiceData.currencySymbol,
            status = invoiceData.status,
            pdfPath = pdfPath,
            pngPath = pngPath
        )
        invoiceDao.insertInvoice(entity)
    }
    
    suspend fun deleteInvoice(invoiceId: String) {
        invoiceDao.deleteInvoiceById(invoiceId)
    }
    
    suspend fun updateStatus(invoiceId: String, status: String) {
        invoiceDao.updateInvoiceStatus(invoiceId, status)
    }
    
    fun searchInvoices(query: String): Flow<List<InvoiceEntity>> {
        return invoiceDao.searchInvoices(query)
    }
    
    fun entityToInvoiceData(entity: InvoiceEntity): InvoiceDataTool {
        val itemsType = object : TypeToken<List<InvoiceItemData>>() {}.type
        val items: List<InvoiceItemData> = try {
            gson.fromJson(entity.itemsJson, itemsType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        
        return InvoiceDataTool(
            id = entity.id,
            invoiceNumber = entity.invoiceNumber,
            invoiceDate = entity.invoiceDate,
            dueDate = entity.dueDate,
            businessInfo = BusinessInfoTool(
                businessName = entity.businessName,
                address = entity.businessAddress,
                phone = entity.businessPhone,
                email = entity.businessEmail,
                logoUri = entity.businessLogoUri,
                taxNumber = entity.taxNumber
            ),
            clientInfo = ClientInfoTool(
                name = entity.clientName,
                address = entity.clientAddress,
                phone = entity.clientPhone,
                email = entity.clientEmail
            ),
            items = items,
            subtotal = entity.subtotal,
            taxRate = entity.taxRate,
            taxAmount = entity.taxAmount,
            discount = entity.discount,
            totalAmount = entity.totalAmount,
            notes = entity.notes,
            bankDetails = entity.bankDetails,
            currencyCode = entity.currencyCode,
            currencySymbol = entity.currencySymbol,
            status = entity.status
        )
    }
}
