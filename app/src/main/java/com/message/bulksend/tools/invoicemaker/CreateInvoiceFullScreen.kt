package com.message.bulksend.tools.invoicemaker

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.message.bulksend.tools.invoicemaker.database.InvoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.util.UUID

// Currency data class
data class CurrencyData(
    val countryAndCurrency: String,
    val code: String,
    val symbol: String
)

// Function to load currencies from CSV
fun loadCurrenciesFromCsv(context: Context): List<CurrencyData> {
    return try {
        context.assets.open("Currency.csv").bufferedReader().useLines { lines ->
            lines.drop(1) // Skip header
                .mapNotNull { line ->
                    val parts = line.split(",").map { it.trim().removeSurrounding("\"") }
                    if (parts.size >= 3) {
                        val symbol = try {
                            parts[2].replace("\\u", "").chunked(4).map { 
                                it.toInt(16).toChar() 
                            }.joinToString("")
                        } catch (e: Exception) {
                            parts[1] // Use code as fallback
                        }
                        CurrencyData(parts[0], parts[1], symbol)
                    } else null
                }.toList()
        }
    } catch (e: Exception) {
        // Return default currencies if CSV fails
        listOf(
            CurrencyData("India Rupee", "INR", "₹"),
            CurrencyData("United States Dollar", "USD", "$"),
            CurrencyData("Euro Member Countries", "EUR", "€")
        )
    }
}

// Colors
private val PrimaryColor = Color(0xFF6366F1)
private val SecondaryColor = Color(0xFF8B5CF6)
private val SuccessColor = Color(0xFF10B981)
private val DangerColor = Color(0xFFEF4444)
private val WarningColor = Color(0xFFF59E0B)
private val BackgroundColor = Color(0xFFF8FAFC)
private val CardColor = Color.White
private val TextPrimary = Color(0xFF1E293B)
private val TextSecondary = Color(0xFF64748B)
private val BorderColor = Color(0xFFE2E8F0)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInvoiceFullScreen(
    clientName: String = "",
    clientPhone: String = "",
    onBack: () -> Unit,
    onInvoiceSaved: () -> Unit,
    onPreview: (InvoiceDataTool) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsManager = remember { InvoiceSettingsManagerTool(context) }
    val invoiceGenerator = remember { InvoiceGeneratorTool(context) }
    val invoiceRepository = remember { InvoiceRepository(context) }
    val settings = remember { settingsManager.getSettings() }
    
    var invoiceNumber by remember { mutableStateOf(settingsManager.getNextInvoiceNumber()) }
    var invoiceDate by remember { mutableStateOf(System.currentTimeMillis()) }
    
    var businessName by remember { mutableStateOf(settings.businessName) }
    var businessAddress by remember { mutableStateOf(settings.businessAddress) }
    var businessPhone by remember { mutableStateOf(settings.businessPhone) }
    var businessEmail by remember { mutableStateOf(settings.businessEmail) }
    var logoUri by remember { mutableStateOf(settings.logoUri) }
    var taxNumber by remember { mutableStateOf(settings.taxNumber) }
    var currencySymbol by remember { mutableStateOf(settings.currencySymbol) }
    var currencyCode by remember { mutableStateOf(settings.currencyCode) }
    
    var clientNameState by remember { mutableStateOf(clientName) }
    var clientAddress by remember { mutableStateOf("") }
    var clientPhoneState by remember { mutableStateOf(clientPhone) }
    var clientEmail by remember { mutableStateOf("") }
    
    var items by remember { mutableStateOf(listOf(InvoiceItemData())) }
    var taxRate by remember { mutableStateOf(settings.defaultTaxRate.toString()) }
    var discount by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var bankDetails by remember { mutableStateOf(settings.bankDetails) }
    var invoiceStatus by remember { mutableStateOf("CREATED") }
    
    var showExportDialog by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showStatusPicker by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var expandedSection by remember { mutableStateOf(0) }
    
    // Load currencies from CSV
    val currencies = remember { loadCurrenciesFromCsv(context) }
    var currencySearchQuery by remember { mutableStateOf("") }
    
    val currencyFormat = remember { DecimalFormat("#,##0.00") }
    
    val subtotal = items.sumOf { it.quantity * it.rate }
    val taxAmount = subtotal * ((taxRate.toDoubleOrNull() ?: 0.0) / 100)
    val discountAmount = discount.toDoubleOrNull() ?: 0.0
    val totalAmount = subtotal + taxAmount - discountAmount
    
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            logoUri = it.toString()
            settingsManager.saveLogo(it.toString())
        }
    }
    
    fun buildInvoiceData(): InvoiceDataTool {
        return InvoiceDataTool(
            invoiceNumber = invoiceNumber,
            invoiceDate = invoiceDate,
            businessInfo = BusinessInfoTool(businessName, businessAddress, businessPhone, businessEmail, "", logoUri, taxNumber),
            clientInfo = ClientInfoTool(clientNameState, clientAddress, clientPhoneState, clientEmail),
            items = items.filter { it.description.isNotEmpty() },
            subtotal = subtotal, taxRate = taxRate.toDoubleOrNull() ?: 0.0, taxAmount = taxAmount,
            discount = discountAmount, totalAmount = totalAmount, notes = notes, bankDetails = bankDetails,
            currencyCode = currencyCode, currencySymbol = currencySymbol, status = invoiceStatus
        )
    }

    Scaffold(
        containerColor = BackgroundColor,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(PrimaryColor, SecondaryColor)),
                        RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Create Invoice", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(invoiceNumber, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { 
                                val invoice = buildInvoiceData()
                                onPreview(invoice)
                            },
                            enabled = clientNameState.isNotEmpty() && items.any { it.description.isNotEmpty() }
                        ) {
                            Text("PREVIEW", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { showExportDialog = true },
                            enabled = clientNameState.isNotEmpty() && items.any { it.description.isNotEmpty() }
                        ) {
                            Text("EXPORT", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Total Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Amount", fontSize = 14.sp, color = TextSecondary)
                            Text("$currencySymbol${currencyFormat.format(totalAmount)}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = SuccessColor)
                        }
                        Box(
                            modifier = Modifier.size(56.dp).background(SuccessColor.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Receipt, null, tint = SuccessColor, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
            
            // Business Section
            item {
                BeautifulSection(
                    title = "Your Business",
                    icon = Icons.Default.Business,
                    color = PrimaryColor,
                    expanded = expandedSection == 0,
                    onToggle = { expandedSection = if (expandedSection == 0) -1 else 0 }
                ) {
                    // Logo
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFF1F5F9)).border(2.dp, PrimaryColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .clickable { logoPicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (logoUri != null) {
                                AsyncImage(
                                    model = logoUri, 
                                    contentDescription = "Logo", 
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)), 
                                    contentScale = ContentScale.Crop,
                                    colorFilter = null
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.AddPhotoAlternate, null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                                    Text("Logo", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Add Business Logo", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("Tap to upload image", fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                    BeautifulTextField("Business Name", businessName, Icons.Default.Business) { businessName = it }
                    BeautifulTextField("Address", businessAddress, Icons.Default.LocationOn) { businessAddress = it }
                    BeautifulTextField("Phone", businessPhone, Icons.Default.Phone) { businessPhone = it }
                    BeautifulTextField("Email", businessEmail, Icons.Default.Email) { businessEmail = it }
                    BeautifulTextField("Tax ID", taxNumber, Icons.Default.Numbers) { taxNumber = it }
                    
                    // Currency Selector
                    Spacer(Modifier.height(8.dp))
                    Text("Currency", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().clickable { showCurrencyPicker = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CurrencyExchange, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("$currencySymbol $currencyCode", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    Text("Tap to change currency", fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                            Icon(Icons.Default.KeyboardArrowRight, null, tint = TextSecondary)
                        }
                    }
                    
                    // Status Selector
                    Spacer(Modifier.height(16.dp))
                    Text("Invoice Status", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().clickable { showStatusPicker = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val statusColor = when (invoiceStatus) {
                                    "PAID" -> SuccessColor
                                    "SENT" -> Color(0xFF3B82F6)
                                    "CANCELLED" -> DangerColor
                                    else -> WarningColor
                                }
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(statusColor, CircleShape)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(invoiceStatus, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    Text("Tap to change status", fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                            Icon(Icons.Default.KeyboardArrowRight, null, tint = TextSecondary)
                        }
                    }
                }
            }
            
            // Client Section
            item {
                BeautifulSection(
                    title = "Bill To",
                    icon = Icons.Default.Person,
                    color = SuccessColor,
                    expanded = expandedSection == 1,
                    onToggle = { expandedSection = if (expandedSection == 1) -1 else 1 }
                ) {
                    BeautifulTextField("Client Name *", clientNameState, Icons.Default.Person) { clientNameState = it }
                    BeautifulTextField("Address", clientAddress, Icons.Default.LocationOn) { clientAddress = it }
                    BeautifulTextField("Phone", clientPhoneState, Icons.Default.Phone) { clientPhoneState = it }
                    BeautifulTextField("Email", clientEmail, Icons.Default.Email) { clientEmail = it }
                }
            }
            
            // Items Section
            item {
                BeautifulSection(
                    title = "Items (${items.count { it.description.isNotEmpty() }})",
                    icon = Icons.Default.ShoppingCart,
                    color = WarningColor,
                    expanded = expandedSection == 2,
                    onToggle = { expandedSection = if (expandedSection == 2) -1 else 2 }
                ) {
                    items.forEachIndexed { index, item ->
                        BeautifulItemRow(
                            item = item,
                            currencySymbol = currencySymbol,
                            onUpdate = { items = items.toMutableList().apply { set(index, it) } },
                            onDelete = { if (items.size > 1) items = items.toMutableList().apply { removeAt(index) } }
                        )
                        if (index < items.lastIndex) Spacer(Modifier.height(12.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { items = items + InvoiceItemData() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SuccessColor)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Item", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            
            // Summary Section
            item {
                BeautifulSection(
                    title = "Summary",
                    icon = Icons.Default.Calculate,
                    color = SecondaryColor,
                    expanded = expandedSection == 3,
                    onToggle = { expandedSection = if (expandedSection == 3) -1 else 3 }
                ) {
                    SummaryRow("Subtotal", "$currencySymbol${currencyFormat.format(subtotal)}")
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Tax Rate (%)", color = TextSecondary, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = taxRate, onValueChange = { taxRate = it },
                            modifier = Modifier.width(100.dp), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SecondaryColor, unfocusedBorderColor = BorderColor),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    if (taxAmount > 0) { Spacer(Modifier.height(8.dp)); SummaryRow("Tax", "$currencySymbol${currencyFormat.format(taxAmount)}") }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Discount ($currencySymbol)", color = TextSecondary, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = discount, onValueChange = { discount = it },
                            modifier = Modifier.width(100.dp), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SecondaryColor, unfocusedBorderColor = BorderColor),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = BorderColor)
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("$currencySymbol${currencyFormat.format(totalAmount)}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SuccessColor)
                    }
                }
            }
            
            // Notes
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Additional Info", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = notes, onValueChange = { notes = it }, label = { Text("Notes") },
                            modifier = Modifier.fillMaxWidth(), minLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryColor, unfocusedBorderColor = BorderColor),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = bankDetails, onValueChange = { bankDetails = it }, label = { Text("Bank Details") },
                            modifier = Modifier.fillMaxWidth(), minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryColor, unfocusedBorderColor = BorderColor),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
            
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    
    if (showExportDialog) {
        BeautifulExportDialog(
            isGenerating = isGenerating,
            onDismiss = { showExportDialog = false },
            onExportPdf = {
                coroutineScope.launch {
                    isGenerating = true
                    val invoice = buildInvoiceData()
                    withContext(Dispatchers.IO) {
                        val file = invoiceGenerator.generatePdf(invoice)
                        // Save invoice to database
                        invoiceRepository.saveInvoice(invoice, pdfPath = file?.absolutePath)
                        withContext(Dispatchers.Main) {
                            isGenerating = false
                            if (file != null) { 
                                invoiceGenerator.shareInvoice(file, "application/pdf")
                                Toast.makeText(context, "Invoice saved!", Toast.LENGTH_SHORT).show()
                                onInvoiceSaved() 
                            }
                            else Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onExportPng = {
                coroutineScope.launch {
                    isGenerating = true
                    val invoice = buildInvoiceData()
                    withContext(Dispatchers.IO) {
                        val file = invoiceGenerator.generatePng(invoice)
                        // Save invoice to database
                        invoiceRepository.saveInvoice(invoice, pngPath = file?.absolutePath)
                        withContext(Dispatchers.Main) {
                            isGenerating = false
                            if (file != null) { 
                                invoiceGenerator.shareInvoice(file, "image/png")
                                Toast.makeText(context, "Invoice saved!", Toast.LENGTH_SHORT).show()
                                onInvoiceSaved() 
                            }
                            else Toast.makeText(context, "Failed to generate PNG", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }
    
    // Currency Picker Dialog
    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            currencies = currencies,
            searchQuery = currencySearchQuery,
            onSearchQueryChange = { currencySearchQuery = it },
            onCurrencySelected = { currency ->
                currencySymbol = currency.symbol
                currencyCode = currency.code
                settingsManager.saveCurrency(currency.code, currency.symbol)
                showCurrencyPicker = false
                currencySearchQuery = ""
            },
            onDismiss = { 
                showCurrencyPicker = false
                currencySearchQuery = ""
            }
        )
    }
    
    // Status Picker Dialog
    if (showStatusPicker) {
        InvoiceStatusPickerDialog(
            currentStatus = invoiceStatus,
            onStatusSelected = { newStatus ->
                invoiceStatus = newStatus
                showStatusPicker = false
            },
            onDismiss = { showStatusPicker = false }
        )
    }
}


@Composable
fun BeautifulSection(
    title: String,
    icon: ImageVector,
    color: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null, tint = TextSecondary
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) { content() }
            }
        }
    }
}

@Composable
fun BeautifulTextField(
    label: String,
    value: String,
    icon: ImageVector,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextSecondary) },
        leadingIcon = { Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(20.dp)) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryColor,
            unfocusedBorderColor = BorderColor,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun BeautifulItemRow(item: InvoiceItemData, currencySymbol: String, onUpdate: (InvoiceItemData) -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = item.description,
                    onValueChange = { onUpdate(item.copy(description = it)) },
                    label = { Text("Description") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WarningColor, unfocusedBorderColor = BorderColor),
                    shape = RoundedCornerShape(12.dp)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = DangerColor)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = if (item.quantity == 0) "" else item.quantity.toString(),
                    onValueChange = { onUpdate(item.copy(quantity = it.toIntOrNull() ?: 0)) },
                    label = { Text("Qty") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WarningColor, unfocusedBorderColor = BorderColor),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = if (item.rate == 0.0) "" else item.rate.toString(),
                    onValueChange = { onUpdate(item.copy(rate = it.toDoubleOrNull() ?: 0.0)) },
                    label = { Text("Rate") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WarningColor, unfocusedBorderColor = BorderColor),
                    shape = RoundedCornerShape(12.dp)
                )
                Box(
                    modifier = Modifier.weight(1f).height(56.dp).background(SuccessColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$currencySymbol${DecimalFormat("#,##0").format(item.quantity * item.rate)}", color = SuccessColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary)
        Text(value, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun BeautifulExportDialog(isGenerating: Boolean, onDismiss: () -> Unit, onExportPdf: () -> Unit, onExportPng: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, null, tint = PrimaryColor)
                Spacer(Modifier.width(12.dp))
                Text("Export Invoice", fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            if (isGenerating) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    CircularProgressIndicator(color = PrimaryColor, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Generating invoice...", color = TextSecondary)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose export format:", color = TextSecondary)
                    Button(
                        onClick = onExportPdf,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DangerColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Export as PDF", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Button(
                        onClick = onExportPng,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Image, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Export as PNG", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (!isGenerating) TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = CardColor,
        shape = RoundedCornerShape(24.dp)
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerDialog(
    currencies: List<CurrencyData>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCurrencySelected: (CurrencyData) -> Unit,
    onDismiss: () -> Unit
) {
    val filteredCurrencies = remember(searchQuery, currencies) {
        if (searchQuery.isEmpty()) currencies
        else currencies.filter {
            it.countryAndCurrency.contains(searchQuery, ignoreCase = true) ||
            it.code.contains(searchQuery, ignoreCase = true)
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CurrencyExchange, null, tint = PrimaryColor)
                    Spacer(Modifier.width(12.dp))
                    Text("Select Currency", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search currency...", color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryColor,
                        unfocusedBorderColor = BorderColor
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredCurrencies) { currency ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCurrencySelected(currency) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(PrimaryColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    currency.symbol,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryColor
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    currency.code,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    currency.countryAndCurrency,
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = CardColor,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun InvoiceStatusPickerDialog(
    currentStatus: String,
    onStatusSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val statusOptions = listOf(
        "CREATED" to WarningColor,
        "SENT" to Color(0xFF3B82F6),
        "PAID" to SuccessColor,
        "CANCELLED" to DangerColor
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Select Invoice Status", 
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Choose the current status of this invoice:",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
                statusOptions.forEach { (status, color) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStatusSelected(status) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (status == currentStatus) 
                                color.copy(alpha = 0.1f) 
                            else 
                                Color(0xFFF8FAFC)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(color, CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    status,
                                    fontSize = 16.sp,
                                    fontWeight = if (status == currentStatus) FontWeight.Bold else FontWeight.Medium,
                                    color = if (status == currentStatus) color else TextPrimary
                                )
                                Text(
                                    when (status) {
                                        "CREATED" -> "Draft invoice, not sent yet"
                                        "SENT" -> "Invoice sent to client"
                                        "PAID" -> "Payment received"
                                        "CANCELLED" -> "Invoice cancelled"
                                        else -> ""
                                    },
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            if (status == currentStatus) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = CardColor,
        shape = RoundedCornerShape(20.dp)
    )
}