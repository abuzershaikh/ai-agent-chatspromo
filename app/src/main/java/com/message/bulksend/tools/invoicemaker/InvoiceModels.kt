package com.message.bulksend.tools.invoicemaker

import java.util.UUID

/**
 * World Currencies with Unicode Symbols
 */
data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val name: String,
    val country: String
)

val WORLD_CURRENCIES = listOf(
    CurrencyInfo("INR", "₹", "Indian Rupee", "India"),
    CurrencyInfo("USD", "$", "US Dollar", "United States"),
    CurrencyInfo("EUR", "€", "Euro", "European Union"),
    CurrencyInfo("GBP", "£", "British Pound", "United Kingdom"),
    CurrencyInfo("JPY", "¥", "Japanese Yen", "Japan"),
    CurrencyInfo("CNY", "¥", "Chinese Yuan", "China"),
    CurrencyInfo("AUD", "A$", "Australian Dollar", "Australia"),
    CurrencyInfo("CAD", "C$", "Canadian Dollar", "Canada"),
    CurrencyInfo("CHF", "Fr", "Swiss Franc", "Switzerland"),
    CurrencyInfo("AED", "د.إ", "UAE Dirham", "UAE"),
    CurrencyInfo("SAR", "﷼", "Saudi Riyal", "Saudi Arabia"),
    CurrencyInfo("SGD", "S$", "Singapore Dollar", "Singapore"),
    CurrencyInfo("HKD", "HK$", "Hong Kong Dollar", "Hong Kong"),
    CurrencyInfo("MYR", "RM", "Malaysian Ringgit", "Malaysia"),
    CurrencyInfo("THB", "฿", "Thai Baht", "Thailand"),
    CurrencyInfo("IDR", "Rp", "Indonesian Rupiah", "Indonesia"),
    CurrencyInfo("PHP", "₱", "Philippine Peso", "Philippines"),
    CurrencyInfo("VND", "₫", "Vietnamese Dong", "Vietnam"),
    CurrencyInfo("KRW", "₩", "South Korean Won", "South Korea"),
    CurrencyInfo("BDT", "৳", "Bangladeshi Taka", "Bangladesh"),
    CurrencyInfo("PKR", "₨", "Pakistani Rupee", "Pakistan"),
    CurrencyInfo("LKR", "Rs", "Sri Lankan Rupee", "Sri Lanka"),
    CurrencyInfo("NPR", "रू", "Nepalese Rupee", "Nepal"),
    CurrencyInfo("ZAR", "R", "South African Rand", "South Africa"),
    CurrencyInfo("BRL", "R$", "Brazilian Real", "Brazil"),
    CurrencyInfo("MXN", "$", "Mexican Peso", "Mexico"),
    CurrencyInfo("RUB", "₽", "Russian Ruble", "Russia"),
    CurrencyInfo("TRY", "₺", "Turkish Lira", "Turkey"),
    CurrencyInfo("PLN", "zł", "Polish Zloty", "Poland"),
    CurrencyInfo("SEK", "kr", "Swedish Krona", "Sweden"),
    CurrencyInfo("NOK", "kr", "Norwegian Krone", "Norway"),
    CurrencyInfo("DKK", "kr", "Danish Krone", "Denmark"),
    CurrencyInfo("NZD", "NZ$", "New Zealand Dollar", "New Zealand"),
    CurrencyInfo("EGP", "E£", "Egyptian Pound", "Egypt"),
    CurrencyInfo("NGN", "₦", "Nigerian Naira", "Nigeria"),
    CurrencyInfo("KES", "KSh", "Kenyan Shilling", "Kenya"),
    CurrencyInfo("GHS", "₵", "Ghanaian Cedi", "Ghana"),
    CurrencyInfo("ILS", "₪", "Israeli Shekel", "Israel"),
    CurrencyInfo("QAR", "﷼", "Qatari Riyal", "Qatar"),
    CurrencyInfo("KWD", "د.ك", "Kuwaiti Dinar", "Kuwait"),
    CurrencyInfo("BHD", "BD", "Bahraini Dinar", "Bahrain"),
    CurrencyInfo("OMR", "﷼", "Omani Rial", "Oman")
)


/**
 * Tax Document Types by Country
 */
data class TaxDocumentType(
    val code: String,
    val name: String,
    val country: String,
    val description: String
)

val TAX_DOCUMENT_TYPES = listOf(
    // India
    TaxDocumentType("GST", "GST Number", "India", "Goods and Services Tax"),
    TaxDocumentType("PAN", "PAN Number", "India", "Permanent Account Number"),
    TaxDocumentType("TAN", "TAN Number", "India", "Tax Deduction Account Number"),
    TaxDocumentType("CIN", "CIN Number", "India", "Corporate Identity Number"),
    
    // United States
    TaxDocumentType("EIN", "EIN", "USA", "Employer Identification Number"),
    TaxDocumentType("SSN", "SSN", "USA", "Social Security Number"),
    TaxDocumentType("ITIN", "ITIN", "USA", "Individual Taxpayer ID"),
    
    // European Union
    TaxDocumentType("VAT", "VAT Number", "EU", "Value Added Tax"),
    TaxDocumentType("EORI", "EORI Number", "EU", "Economic Operators Registration"),
    
    // United Kingdom
    TaxDocumentType("VAT_UK", "VAT Number", "UK", "UK VAT Registration"),
    TaxDocumentType("UTR", "UTR", "UK", "Unique Taxpayer Reference"),
    TaxDocumentType("NI", "NI Number", "UK", "National Insurance"),
    
    // Australia
    TaxDocumentType("ABN", "ABN", "Australia", "Australian Business Number"),
    TaxDocumentType("TFN", "TFN", "Australia", "Tax File Number"),
    TaxDocumentType("ACN", "ACN", "Australia", "Australian Company Number"),
    
    // Canada
    TaxDocumentType("BN", "BN", "Canada", "Business Number"),
    TaxDocumentType("GST_CA", "GST/HST", "Canada", "GST/HST Number"),
    TaxDocumentType("SIN", "SIN", "Canada", "Social Insurance Number"),
    
    // UAE
    TaxDocumentType("TRN", "TRN", "UAE", "Tax Registration Number"),
    TaxDocumentType("VAT_UAE", "VAT Number", "UAE", "UAE VAT Number"),
    
    // Saudi Arabia
    TaxDocumentType("VAT_SA", "VAT Number", "Saudi Arabia", "Saudi VAT Number"),
    TaxDocumentType("CR", "CR Number", "Saudi Arabia", "Commercial Registration"),
    
    // Singapore
    TaxDocumentType("UEN", "UEN", "Singapore", "Unique Entity Number"),
    TaxDocumentType("GST_SG", "GST Number", "Singapore", "Singapore GST"),
    
    // Malaysia
    TaxDocumentType("SST", "SST Number", "Malaysia", "Sales and Service Tax"),
    TaxDocumentType("BRN", "BRN", "Malaysia", "Business Registration"),
    
    // Germany
    TaxDocumentType("UST", "USt-IdNr", "Germany", "VAT ID Number"),
    TaxDocumentType("STEUERNR", "Steuernummer", "Germany", "Tax Number"),
    
    // France
    TaxDocumentType("SIRET", "SIRET", "France", "Business ID Number"),
    TaxDocumentType("TVA", "TVA Number", "France", "French VAT"),
    
    // Japan
    TaxDocumentType("JCT", "JCT Number", "Japan", "Japanese Consumption Tax"),
    TaxDocumentType("CORP_JP", "Corporate Number", "Japan", "法人番号"),
    
    // China
    TaxDocumentType("USCC", "USCC", "China", "Unified Social Credit Code"),
    TaxDocumentType("VAT_CN", "VAT Number", "China", "Chinese VAT"),
    
    // Brazil
    TaxDocumentType("CNPJ", "CNPJ", "Brazil", "Corporate Taxpayer ID"),
    TaxDocumentType("CPF", "CPF", "Brazil", "Individual Taxpayer ID"),
    
    // Mexico
    TaxDocumentType("RFC", "RFC", "Mexico", "Federal Taxpayer Registry"),
    
    // South Africa
    TaxDocumentType("VAT_ZA", "VAT Number", "South Africa", "SA VAT Number"),
    TaxDocumentType("CIPC", "CIPC Number", "South Africa", "Company Registration"),
    
    // Other Common
    TaxDocumentType("TAX_ID", "Tax ID", "Universal", "General Tax ID"),
    TaxDocumentType("BUSINESS_REG", "Business Reg.", "Universal", "Business Registration"),
    TaxDocumentType("TRADE_LICENSE", "Trade License", "Universal", "Trade License Number"),
    TaxDocumentType("OTHER", "Other", "Universal", "Custom Tax Document")
)

/**
 * Invoice Item Data
 */
data class InvoiceItemData(
    val id: String = UUID.randomUUID().toString(),
    val description: String = "",
    val quantity: Int = 1,
    val rate: Double = 0.0
)

/**
 * Business Info
 */
data class BusinessInfoTool(
    val businessName: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val logoUri: String? = null,
    val taxNumber: String = "",
    val taxDocumentType: String = "GST",
    val panNumber: String = ""
)

/**
 * Client Info
 */
data class ClientInfoTool(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val taxNumber: String = ""
)

/**
 * Complete Invoice Data
 */
data class InvoiceDataTool(
    val id: String = UUID.randomUUID().toString(),
    val invoiceNumber: String = "",
    val invoiceDate: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val businessInfo: BusinessInfoTool = BusinessInfoTool(),
    val clientInfo: ClientInfoTool = ClientInfoTool(),
    val items: List<InvoiceItemData> = emptyList(),
    val subtotal: Double = 0.0,
    val taxRate: Double = 0.0,
    val taxAmount: Double = 0.0,
    val discount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val notes: String = "",
    val bankDetails: String = "",
    val currencyCode: String = "INR",
    val currencySymbol: String = "₹",
    val status: String = "CREATED"
)

/**
 * Invoice Settings
 */
data class InvoiceSettingsTool(
    val businessName: String = "",
    val businessAddress: String = "",
    val businessPhone: String = "",
    val businessEmail: String = "",
    val businessWebsite: String = "",
    val logoUri: String? = null,
    val taxNumber: String = "",
    val taxDocumentType: String = "GST",
    val panNumber: String = "",
    val defaultTaxRate: Double = 18.0,
    val defaultTerms: String = "Payment due within 30 days",
    val bankDetails: String = "",
    val invoicePrefix: String = "INV",
    val lastInvoiceNumber: Int = 0,
    val currencyCode: String = "INR",
    val currencySymbol: String = "₹"
)
