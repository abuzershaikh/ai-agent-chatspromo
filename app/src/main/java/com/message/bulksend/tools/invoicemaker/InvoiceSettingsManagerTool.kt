package com.message.bulksend.tools.invoicemaker

import android.content.Context
import android.content.SharedPreferences

class InvoiceSettingsManagerTool(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "invoice_maker_settings", Context.MODE_PRIVATE
    )
    
    fun saveSettings(settings: InvoiceSettingsTool) {
        prefs.edit().apply {
            putString("business_name", settings.businessName)
            putString("business_address", settings.businessAddress)
            putString("business_phone", settings.businessPhone)
            putString("business_email", settings.businessEmail)
            putString("business_website", settings.businessWebsite)
            putString("logo_uri", settings.logoUri)
            putString("tax_number", settings.taxNumber)
            putString("tax_document_type", settings.taxDocumentType)
            putString("pan_number", settings.panNumber)
            putFloat("default_tax_rate", settings.defaultTaxRate.toFloat())
            putString("default_terms", settings.defaultTerms)
            putString("bank_details", settings.bankDetails)
            putString("invoice_prefix", settings.invoicePrefix)
            putInt("last_invoice_number", settings.lastInvoiceNumber)
            putString("currency_code", settings.currencyCode)
            putString("currency_symbol", settings.currencySymbol)
            apply()
        }
    }
    
    fun getSettings(): InvoiceSettingsTool {
        return InvoiceSettingsTool(
            businessName = prefs.getString("business_name", "") ?: "",
            businessAddress = prefs.getString("business_address", "") ?: "",
            businessPhone = prefs.getString("business_phone", "") ?: "",
            businessEmail = prefs.getString("business_email", "") ?: "",
            businessWebsite = prefs.getString("business_website", "") ?: "",
            logoUri = prefs.getString("logo_uri", null),
            taxNumber = prefs.getString("tax_number", "") ?: "",
            taxDocumentType = prefs.getString("tax_document_type", "GST") ?: "GST",
            panNumber = prefs.getString("pan_number", "") ?: "",
            defaultTaxRate = prefs.getFloat("default_tax_rate", 18f).toDouble(),
            defaultTerms = prefs.getString("default_terms", "Payment due within 30 days") ?: "",
            bankDetails = prefs.getString("bank_details", "") ?: "",
            invoicePrefix = prefs.getString("invoice_prefix", "INV") ?: "INV",
            lastInvoiceNumber = prefs.getInt("last_invoice_number", 0),
            currencyCode = prefs.getString("currency_code", "INR") ?: "INR",
            currencySymbol = prefs.getString("currency_symbol", "₹") ?: "₹"
        )
    }
    
    fun getNextInvoiceNumber(): String {
        val settings = getSettings()
        val nextNumber = settings.lastInvoiceNumber + 1
        saveSettings(settings.copy(lastInvoiceNumber = nextNumber))
        return "${settings.invoicePrefix}-${String.format("%04d", nextNumber)}"
    }
    
    fun saveLogo(uri: String) {
        prefs.edit().putString("logo_uri", uri).apply()
    }
    
    fun saveCurrency(code: String, symbol: String) {
        prefs.edit().apply {
            putString("currency_code", code)
            putString("currency_symbol", symbol)
            apply()
        }
    }
    
    fun saveTaxDocumentType(type: String) {
        prefs.edit().putString("tax_document_type", type).apply()
    }
}
