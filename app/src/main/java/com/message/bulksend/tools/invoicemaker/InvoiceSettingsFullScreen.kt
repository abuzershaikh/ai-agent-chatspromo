package com.message.bulksend.tools.invoicemaker

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

private val PrimaryColor = Color(0xFF6366F1)
private val SecondaryColor = Color(0xFF8B5CF6)
private val SuccessColor = Color(0xFF10B981)
private val WarningColor = Color(0xFFF59E0B)
private val DangerColor = Color(0xFFEF4444)
private val PinkColor = Color(0xFFEC4899)
private val BlueColor = Color(0xFF3B82F6)
private val BackgroundColor = Color(0xFFF8FAFC)
private val CardColor = Color.White
private val TextPrimary = Color(0xFF1E293B)
private val TextSecondary = Color(0xFF64748B)
private val BorderColor = Color(0xFFE2E8F0)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceSettingsFullScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { InvoiceSettingsManagerTool(context) }
    var settings by remember { mutableStateOf(settingsManager.getSettings()) }
    
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showTaxDocPicker by remember { mutableStateOf(false) }
    var currencySearch by remember { mutableStateOf("") }
    var taxDocSearch by remember { mutableStateOf("") }
    
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { settings = settings.copy(logoUri = it.toString()) }
    }
    
    fun saveSettings() {
        settingsManager.saveSettings(settings)
        Toast.makeText(context, "Settings saved!", Toast.LENGTH_SHORT).show()
    }
    
    val selectedCurrency = WORLD_CURRENCIES.find { it.code == settings.currencyCode } ?: WORLD_CURRENCIES[0]
    val selectedTaxDoc = TAX_DOCUMENT_TYPES.find { it.code == settings.taxDocumentType } ?: TAX_DOCUMENT_TYPES[0]

    Scaffold(containerColor = BackgroundColor) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 100.dp)) {
            // Header
            item {
                Box(modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(PrimaryColor, SecondaryColor, Color(0xFFA855F7))), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))) {
                    Box(modifier = Modifier.size(120.dp).offset(x = (-30).dp, y = (-20).dp).background(Color.White.copy(alpha = 0.1f), CircleShape))
                    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack, modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)) {
                                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                            }
                            TextButton(onClick = { saveSettings(); onBack() }) { Text("SAVE", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(56.dp).background(Color.White, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Settings, null, tint = PrimaryColor, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Invoice Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Configure your business details", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            
            // Currency Section
            item {
                SettingsSection(title = "Currency", icon = Icons.Default.AttachMoney, color = SuccessColor) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { showCurrencyPicker = true },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(48.dp).background(SuccessColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                    Text(selectedCurrency.symbol, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SuccessColor)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(selectedCurrency.name, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text("${selectedCurrency.code} • ${selectedCurrency.country}", fontSize = 13.sp, color = TextSecondary)
                                }
                            }
                            Icon(Icons.Default.KeyboardArrowDown, null, tint = TextSecondary)
                        }
                    }
                }
            }
            
            // Logo Section
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp).shadow(4.dp, RoundedCornerShape(24.dp)), colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(24.dp)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Business Logo", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(20.dp))
                        Box(modifier = Modifier.size(120.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(PrimaryColor.copy(alpha = 0.05f), SecondaryColor.copy(alpha = 0.05f)))).border(3.dp, Brush.linearGradient(listOf(PrimaryColor, SecondaryColor)), RoundedCornerShape(20.dp)).clickable { logoPicker.launch("image/*") }, contentAlignment = Alignment.Center) {
                            if (settings.logoUri != null) AsyncImage(model = settings.logoUri, contentDescription = "Logo", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop, colorFilter = null)
                            else Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AddPhotoAlternate, null, tint = PrimaryColor, modifier = Modifier.size(40.dp)); Text("Tap to add", fontSize = 13.sp, color = TextSecondary) }
                        }
                        if (settings.logoUri != null) { Spacer(Modifier.height(16.dp)); TextButton(onClick = { settings = settings.copy(logoUri = null) }) { Icon(Icons.Default.Delete, null, tint = DangerColor); Text("Remove Logo", color = DangerColor) } }
                    }
                }
            }
            
            // Business Info
            item {
                SettingsSection(title = "Business Information", icon = Icons.Default.Business, color = PrimaryColor) {
                    SettingsField("Business Name", settings.businessName, Icons.Default.Business) { settings = settings.copy(businessName = it) }
                    SettingsField("Address", settings.businessAddress, Icons.Default.LocationOn) { settings = settings.copy(businessAddress = it) }
                    SettingsField("Phone", settings.businessPhone, Icons.Default.Phone) { settings = settings.copy(businessPhone = it) }
                    SettingsField("Email", settings.businessEmail, Icons.Default.Email) { settings = settings.copy(businessEmail = it) }
                    SettingsField("Website", settings.businessWebsite, Icons.Default.Language) { settings = settings.copy(businessWebsite = it) }
                }
            }
            
            // Tax Info with Document Type Picker
            item {
                SettingsSection(title = "Tax Information", icon = Icons.Default.Receipt, color = WarningColor) {
                    // Tax Document Type Picker
                    Text("Tax Document Type", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                    Card(modifier = Modifier.fillMaxWidth().clickable { showTaxDocPicker = true }, colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)), shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(40.dp).background(WarningColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Description, null, tint = WarningColor, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(selectedTaxDoc.name, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text("${selectedTaxDoc.country} • ${selectedTaxDoc.description}", fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                            Icon(Icons.Default.KeyboardArrowDown, null, tint = TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SettingsField("${selectedTaxDoc.name}", settings.taxNumber, Icons.Default.Numbers) { settings = settings.copy(taxNumber = it) }
                    SettingsField("Secondary Tax ID (Optional)", settings.panNumber, Icons.Default.CreditCard) { settings = settings.copy(panNumber = it) }
                    OutlinedTextField(value = settings.defaultTaxRate.toString(), onValueChange = { settings = settings.copy(defaultTaxRate = it.toDoubleOrNull() ?: 18.0) }, label = { Text("Default Tax Rate (%)") }, leadingIcon = { Icon(Icons.Default.Percent, null, tint = TextSecondary) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WarningColor, unfocusedBorderColor = BorderColor), shape = RoundedCornerShape(12.dp))
                }
            }
            
            // Invoice Settings
            item {
                SettingsSection(title = "Invoice Settings", icon = Icons.Default.Settings, color = SecondaryColor) {
                    SettingsField("Invoice Prefix", settings.invoicePrefix, Icons.Default.Tag) { settings = settings.copy(invoicePrefix = it) }
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)), shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Numbers, null, tint = SecondaryColor); Spacer(Modifier.width(12.dp)); Text("Last Invoice Number", color = TextSecondary) }
                            Text("#${settings.lastInvoiceNumber}", fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }
            }
            
            // Bank Details
            item {
                SettingsSection(title = "Bank Details", icon = Icons.Default.AccountBalance, color = BlueColor) {
                    OutlinedTextField(value = settings.bankDetails, onValueChange = { settings = settings.copy(bankDetails = it) }, label = { Text("Bank Account Details") }, placeholder = { Text("Bank Name\nAccount Number\nIFSC/SWIFT Code") }, modifier = Modifier.fillMaxWidth(), minLines = 4, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BlueColor, unfocusedBorderColor = BorderColor), shape = RoundedCornerShape(12.dp))
                }
            }
            
            // Terms
            item {
                SettingsSection(title = "Default Terms", icon = Icons.Default.Description, color = PinkColor) {
                    OutlinedTextField(value = settings.defaultTerms, onValueChange = { settings = settings.copy(defaultTerms = it) }, label = { Text("Terms & Conditions") }, modifier = Modifier.fillMaxWidth(), minLines = 3, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PinkColor, unfocusedBorderColor = BorderColor), shape = RoundedCornerShape(12.dp))
                }
            }
            
            // Save Button
            item {
                Button(onClick = { saveSettings(); onBack() }, modifier = Modifier.fillMaxWidth().padding(20.dp).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = SuccessColor), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Save, null); Spacer(Modifier.width(12.dp)); Text("Save Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    
    // Currency Picker Dialog
    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            searchQuery = currencySearch,
            onSearchChange = { currencySearch = it },
            onSelect = { currency -> settings = settings.copy(currencyCode = currency.code, currencySymbol = currency.symbol); showCurrencyPicker = false },
            onDismiss = { showCurrencyPicker = false }
        )
    }
    
    // Tax Document Picker Dialog
    if (showTaxDocPicker) {
        TaxDocPickerDialog(
            searchQuery = taxDocSearch,
            onSearchChange = { taxDocSearch = it },
            onSelect = { taxDoc -> settings = settings.copy(taxDocumentType = taxDoc.code); showTaxDocPicker = false },
            onDismiss = { showTaxDocPicker = false }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerDialog(searchQuery: String, onSearchChange: (String) -> Unit, onSelect: (CurrencyInfo) -> Unit, onDismiss: () -> Unit) {
    val filteredCurrencies = WORLD_CURRENCIES.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.code.contains(searchQuery, ignoreCase = true) || it.country.contains(searchQuery, ignoreCase = true)
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Select Currency", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery, onValueChange = onSearchChange,
                    placeholder = { Text("Search currency...", color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SuccessColor, unfocusedBorderColor = BorderColor),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        text = {
            LazyColumn(modifier = Modifier.height(400.dp)) {
                items(filteredCurrencies) { currency ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelect(currency) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(44.dp).background(SuccessColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Text(currency.symbol, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SuccessColor)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(currency.name, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 15.sp)
                                Text("${currency.code} • ${currency.country}", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
        containerColor = CardColor,
        shape = RoundedCornerShape(24.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxDocPickerDialog(searchQuery: String, onSearchChange: (String) -> Unit, onSelect: (TaxDocumentType) -> Unit, onDismiss: () -> Unit) {
    val filteredDocs = TAX_DOCUMENT_TYPES.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.code.contains(searchQuery, ignoreCase = true) || it.country.contains(searchQuery, ignoreCase = true)
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Select Tax Document", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery, onValueChange = onSearchChange,
                    placeholder = { Text("Search by country or type...", color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WarningColor, unfocusedBorderColor = BorderColor),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        text = {
            LazyColumn(modifier = Modifier.height(400.dp)) {
                items(filteredDocs) { doc ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelect(doc) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(44.dp).background(WarningColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Description, null, tint = WarningColor, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(doc.name, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 15.sp)
                                Text("${doc.country} • ${doc.description}", fontSize = 12.sp, color = TextSecondary)
                            }
                            Surface(color = WarningColor.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                                Text(doc.code, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WarningColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
        containerColor = CardColor,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun SettingsSection(title: String, icon: ImageVector, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp).shadow(4.dp, RoundedCornerShape(20.dp)), colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.width(12.dp))
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun SettingsField(label: String, value: String, icon: ImageVector, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, leadingIcon = { Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(20.dp)) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryColor, unfocusedBorderColor = BorderColor), shape = RoundedCornerShape(12.dp))
}
