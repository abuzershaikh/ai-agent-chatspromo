package com.message.bulksend.leadmanager.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.LeadManager
import com.message.bulksend.leadmanager.model.Lead
import com.message.bulksend.leadmanager.model.LeadPriority
import com.message.bulksend.leadmanager.model.LeadStatus
import com.message.bulksend.leadmanager.model.FollowUp
import com.message.bulksend.leadmanager.model.FollowUpType
import com.message.bulksend.leadmanager.model.Product
import com.message.bulksend.leadmanager.customfields.CustomFieldsManager
import com.message.bulksend.leadmanager.customfields.model.CustomField
import com.message.bulksend.leadmanager.customfields.ui.CustomFieldInput
import com.message.bulksend.info.SimCountryDetector
import com.message.bulksend.plan.PrepackActivity
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch

// Country data class for picker
data class CountryDialInfo(
    val name: String,
    val flag: String,
    val code: String,
    val dialCode: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLeadScreen(
    leadManager: LeadManager,
    onBack: () -> Unit,
    onLeadAdded: () -> Unit
) {
    val context = LocalContext.current
    
    // Load countries from JSON
    val countries = remember {
        try {
            val jsonString = context.assets.open("country_dial_info.json").bufferedReader().use { it.readText() }
            val jsonArray = org.json.JSONArray(jsonString)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                CountryDialInfo(
                    name = obj.getString("name"),
                    flag = obj.getString("flag"),
                    code = obj.getString("code"),
                    dialCode = obj.getString("dial_code")
                )
            }
        } catch (e: Exception) { emptyList() }
    }
    
    // Auto-detect country from SIM
    val simCountryDetector = remember { SimCountryDetector(context) }
    val detectedCountry = remember {
        val simCountry = simCountryDetector.getCurrentSimCountry()
        simCountry?.let { info ->
            countries.find { it.code.equals(info.iso, ignoreCase = true) }
        } ?: countries.find { it.code == "PK" } // Default to Pakistan
    }
    
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(detectedCountry) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var countrySearchQuery by remember { mutableStateOf("") }
    var alternatePhone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var emailTouched by remember { mutableStateOf(false) }
    var lastMessage by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    
    var selectedStatus by remember { mutableStateOf(LeadStatus.NEW) }
    var selectedPriority by remember { mutableStateOf(LeadPriority.MEDIUM) }
    
    var selectedSource by remember { mutableStateOf("") }
    var expandedSource by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf(leadManager.getAllSources()) }
    
    var selectedProduct by remember { mutableStateOf("") }
    var expandedProduct by remember { mutableStateOf(false) }
    var products by remember { mutableStateOf(leadManager.getAllProducts()) }
    
    var selectedTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var expandedTags by remember { mutableStateOf(false) }
    val allTags by remember { mutableStateOf(leadManager.getAllTags()) }
    
    var expandedStatus by remember { mutableStateOf(false) }
    var expandedPriority by remember { mutableStateOf(false) }
    
    // Follow-up states
    var scheduleFollowUp by remember { mutableStateOf(false) }
    var followUpTitle by remember { mutableStateOf("") }
    var followUpType by remember { mutableStateOf(FollowUpType.CALL) }
    var followUpDate by remember { mutableStateOf(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }) }
    var followUpTime by remember { mutableStateOf("10:00") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    // Dialog states
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var newSourceName by remember { mutableStateOf("") }
    var showAddProductScreen by remember { mutableStateOf(false) }
    var fieldSettings by remember { mutableStateOf(leadManager.getProductFieldSettings()) }
    
    // Custom Fields
    val customFieldsManager = remember { CustomFieldsManager(context) }
    val customFields by customFieldsManager.getAllActiveFields().collectAsState(initial = emptyList())
    var customFieldValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var customFieldErrors by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showLeadLimitDialog by remember { mutableStateOf(false) }
    var leadLimitMessage by remember { mutableStateOf("Free plan allows only 5 leads. Please upgrade to add more.") }
    val coroutineScope = rememberCoroutineScope()
    
    // Initialize custom field default values
    LaunchedEffect(customFields) {
        val defaults = customFieldsManager.getDefaultValues()
        customFieldValues = defaults + customFieldValues
    }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    // Handle back press
    BackHandler { onBack() }
    
    // Show Add Product Screen
    if (showAddProductScreen) {
        AddEditProductScreen(
            product = null,
            fieldSettings = fieldSettings,
            onBack = { showAddProductScreen = false },
            onSave = { product ->
                leadManager.addProductV2(product)
                leadManager.addProduct(product.name)
                products = leadManager.getAllProducts()
                selectedProduct = product.name
                showAddProductScreen = false
            },
            onOpenSettings = { }
        )
        return
    }
    
    // Add Source Dialog
    if (showAddSourceDialog) {
        AlertDialog(
            onDismissRequest = { showAddSourceDialog = false; newSourceName = "" },
            title = { Text("Add New Source", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newSourceName,
                    onValueChange = { newSourceName = it },
                    label = { Text("Source Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newSourceName.isNotBlank()) {
                        leadManager.addSource(newSourceName.trim())
                        sources = leadManager.getAllSources()
                        selectedSource = newSourceName.trim()
                        newSourceName = ""
                        showAddSourceDialog = false
                    }
                }) { Text("Add", color = Color(0xFF10B981)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddSourceDialog = false; newSourceName = "" }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1a1a2e)
        )
    }
    
    // Date Picker
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = followUpDate.timeInMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { followUpDate.timeInMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
    
    // Time Picker
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = followUpTime.split(":")[0].toInt(),
            initialMinute = followUpTime.split(":")[1].toInt()
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    followUpTime = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = timePickerState) }
        )
    }
    
    // Country Picker Dialog
    if (showCountryPicker) {
        AlertDialog(
            onDismissRequest = { showCountryPicker = false; countrySearchQuery = "" },
            title = {
                Column {
                    Text("Select Country", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = countrySearchQuery,
                        onValueChange = { countrySearchQuery = it },
                        placeholder = { Text("Search country...", color = Color(0xFF94A3B8)) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF64748B)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF64748B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            text = {
                val filteredCountries = countries.filter {
                    it.name.contains(countrySearchQuery, ignoreCase = true) ||
                    it.dialCode.contains(countrySearchQuery) ||
                    it.code.contains(countrySearchQuery, ignoreCase = true)
                }
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(filteredCountries) { country ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCountry = country
                                    showCountryPicker = false
                                    countrySearchQuery = ""
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(country.flag, fontSize = 24.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(country.name, color = Color.White, fontSize = 14.sp)
                                Text(country.dialCode, color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                            if (selectedCountry?.code == country.code) {
                                Icon(Icons.Default.Check, null, tint = Color(0xFF10B981))
                            }
                        }
                        HorizontalDivider(color = Color(0xFF334155))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCountryPicker = false; countrySearchQuery = "" }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1a1a2e),
            modifier = Modifier.fillMaxWidth(0.95f)
        )
    }
    
    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1a1a2e), shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp).background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFF10B981))
                        }
                        Column {
                            Text("Add New Lead", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Fill in the details below", fontSize = 14.sp, color = Color(0xFF94A3B8))
                        }
                    }
                    Icon(Icons.Default.PersonAdd, null, tint = Color(0xFF10B981), modifier = Modifier.size(32.dp))
                }
            }
            
            // Form Content
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Basic Info Card
                item {
                    ModernCard(title = "Basic Information", icon = Icons.Default.Person, iconColor = Color(0xFF3B82F6)) {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it },
                            label = { Text("Full Name *") },
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = Color(0xFF10B981)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            colors = modernTextFieldColors(), shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        
                        // Country Code Selector
                        OutlinedTextField(
                            value = "${selectedCountry?.flag ?: ""} ${selectedCountry?.dialCode ?: ""} - ${selectedCountry?.name ?: "Select Country"}",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Country Code") },
                            leadingIcon = { Icon(Icons.Default.Public, null, tint = Color(0xFF3B82F6)) },
                            modifier = Modifier.fillMaxWidth().clickable { showCountryPicker = true },
                            colors = modernTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                IconButton(onClick = { showCountryPicker = true }) {
                                    Icon(Icons.Default.ArrowDropDown, "Select Country", tint = Color(0xFF3B82F6))
                                }
                            }
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Phone Number
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it.filter { char -> char.isDigit() } },
                            label = { Text("Phone Number") },
                            leadingIcon = { Icon(Icons.Default.Phone, null, tint = Color(0xFF3B82F6)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = modernTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("Enter phone number without country code") }
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Alternate Phone Number
                        OutlinedTextField(
                            value = alternatePhone,
                            onValueChange = { alternatePhone = it.filter { char -> char.isDigit() || char == '+' } },
                            label = { Text("Alternate Phone (Optional)") },
                            leadingIcon = { Icon(Icons.Default.PhoneAndroid, null, tint = Color(0xFF06B6D4)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = modernTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        val isEmailValid = email.isEmpty() || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
                        val showEmailError = emailTouched && email.isNotEmpty() && !isEmailValid
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it.trim().replace(" ", "") },
                            label = { Text("Email") },
                            leadingIcon = { Icon(Icons.Default.Email, null, tint = Color(0xFFEC4899)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && email.isNotEmpty()) emailTouched = true },
                            colors = modernTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            isError = showEmailError,
                            supportingText = if (showEmailError) {{ Text("Invalid email format", color = Color(0xFFEF4444)) }} else null
                        )
                    }
                }
                
                // Status & Priority Card
                item {
                    ModernCard(title = "Lead Classification", icon = Icons.Default.Category, iconColor = Color(0xFF8B5CF6)) {
                        // Status Dropdown
                        ExposedDropdownMenuBox(expanded = expandedStatus, onExpandedChange = { expandedStatus = it }) {
                            OutlinedTextField(
                                value = selectedStatus.displayName, onValueChange = {}, readOnly = true,
                                label = { Text("Status") },
                                leadingIcon = { Icon(Icons.Default.Flag, null, tint = Color(selectedStatus.color)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedStatus) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                colors = modernTextFieldColors(), shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(expanded = expandedStatus, onDismissRequest = { expandedStatus = false }) {
                                LeadStatus.entries.forEach { status ->
                                    DropdownMenuItem(
                                        text = { Text(status.displayName) },
                                        onClick = { selectedStatus = status; expandedStatus = false },
                                        leadingIcon = { Icon(Icons.Default.Circle, null, tint = Color(status.color), modifier = Modifier.size(12.dp)) }
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Priority Dropdown
                        ExposedDropdownMenuBox(expanded = expandedPriority, onExpandedChange = { expandedPriority = it }) {
                            OutlinedTextField(
                                value = selectedPriority.displayName, onValueChange = {}, readOnly = true,
                                label = { Text("Priority") },
                                leadingIcon = { Icon(Icons.Default.PriorityHigh, null, tint = Color(selectedPriority.color)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedPriority) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                colors = modernTextFieldColors(), shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(expanded = expandedPriority, onDismissRequest = { expandedPriority = false }) {
                                LeadPriority.entries.forEach { priority ->
                                    DropdownMenuItem(
                                        text = { Text(priority.displayName) },
                                        onClick = { selectedPriority = priority; expandedPriority = false },
                                        leadingIcon = { Icon(Icons.Default.Circle, null, tint = Color(priority.color), modifier = Modifier.size(12.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Source & Product Card
                item {
                    ModernCard(title = "Source & Product", icon = Icons.Default.Source, iconColor = Color(0xFFF59E0B)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExposedDropdownMenuBox(expanded = expandedSource, onExpandedChange = { expandedSource = it }, modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = selectedSource, onValueChange = {}, readOnly = true,
                                    label = { Text("Lead Source") },
                                    leadingIcon = { Icon(Icons.Default.Source, null, tint = Color(0xFFF59E0B)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedSource) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    colors = modernTextFieldColors(), shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(expanded = expandedSource, onDismissRequest = { expandedSource = false }) {
                                    sources.forEach { src ->
                                        DropdownMenuItem(text = { Text(src) }, onClick = { selectedSource = src; expandedSource = false })
                                    }
                                }
                            }
                            IconButton(
                                onClick = { showAddSourceDialog = true },
                                modifier = Modifier.size(48.dp).background(Color(0xFFF59E0B).copy(alpha = 0.2f), CircleShape)
                            ) { Icon(Icons.Default.Add, "Add Source", tint = Color(0xFFF59E0B)) }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExposedDropdownMenuBox(expanded = expandedProduct, onExpandedChange = { expandedProduct = it }, modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = selectedProduct, onValueChange = {}, readOnly = true,
                                    label = { Text("Product/Service") },
                                    leadingIcon = { Icon(Icons.Default.Inventory, null, tint = Color(0xFF06B6D4)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedProduct) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    colors = modernTextFieldColors(), shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(expanded = expandedProduct, onDismissRequest = { expandedProduct = false }) {
                                    products.forEach { prod ->
                                        DropdownMenuItem(text = { Text(prod) }, onClick = { selectedProduct = prod; expandedProduct = false })
                                    }
                                }
                            }
                            IconButton(
                                onClick = { showAddProductScreen = true },
                                modifier = Modifier.size(48.dp).background(Color(0xFF06B6D4).copy(alpha = 0.2f), CircleShape)
                            ) { Icon(Icons.Default.Add, "Add Product", tint = Color(0xFF06B6D4)) }
                        }
                    }
                }
                
                // Follow-up Scheduling Card (NEW!)
                item {
                    ModernCard(title = "Schedule Follow-up", icon = Icons.Default.Schedule, iconColor = Color(0xFF8B5CF6)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Schedule a follow-up?", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                Text("Set reminder for this lead", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(
                                checked = scheduleFollowUp,
                                onCheckedChange = { scheduleFollowUp = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF8B5CF6),
                                    uncheckedThumbColor = Color(0xFF64748B),
                                    uncheckedTrackColor = Color(0xFF334155)
                                )
                            )
                        }
                        
                        AnimatedVisibility(visible = scheduleFollowUp) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 16.dp)) {
                                OutlinedTextField(
                                    value = followUpTitle, onValueChange = { followUpTitle = it },
                                    label = { Text("Follow-up Title") },
                                    placeholder = { Text("e.g., Call back for pricing") },
                                    leadingIcon = { Icon(Icons.Default.Title, null, tint = Color(0xFF8B5CF6)) },
                                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                                    colors = modernTextFieldColors(), shape = RoundedCornerShape(12.dp)
                                )
                                
                                // Follow-up Type Selection
                                Text("Follow-up Type", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FollowUpType.entries.forEach { type ->
                                        FilterChip(
                                            onClick = { followUpType = type },
                                            label = { Text(type.displayName, fontSize = 12.sp) },
                                            selected = followUpType == type,
                                            leadingIcon = {
                                                Icon(
                                                    when (type) {
                                                        FollowUpType.CALL -> Icons.Default.Phone
                                                        FollowUpType.EMAIL -> Icons.Default.Email
                                                        FollowUpType.MEETING -> Icons.Default.Group
                                                        FollowUpType.WHATSAPP -> Icons.Default.Chat
                                                        FollowUpType.VISIT -> Icons.Default.LocationOn
                                                        FollowUpType.OTHER -> Icons.Default.Event
                                                    }, null, modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(type.color).copy(alpha = 0.3f),
                                                selectedLabelColor = Color(type.color)
                                            )
                                        )
                                    }
                                }
                                
                                // Date & Time Selection
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(followUpDate.time),
                                        onValueChange = {}, readOnly = true,
                                        label = { Text("Date") },
                                        modifier = Modifier.weight(1f).clickable { showDatePicker = true },
                                        trailingIcon = {
                                            IconButton(onClick = { showDatePicker = true }) {
                                                Icon(Icons.Default.DateRange, "Select Date", tint = Color(0xFF8B5CF6))
                                            }
                                        },
                                        colors = modernTextFieldColors(), shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = followUpTime, onValueChange = {}, readOnly = true,
                                        label = { Text("Time") },
                                        modifier = Modifier.weight(1f).clickable { showTimePicker = true },
                                        trailingIcon = {
                                            IconButton(onClick = { showTimePicker = true }) {
                                                Icon(Icons.Default.AccessTime, "Select Time", tint = Color(0xFF8B5CF6))
                                            }
                                        },
                                        colors = modernTextFieldColors(), shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Tags Card
                item {
                    ModernCard(title = "Tags", icon = Icons.Default.Label, iconColor = Color(0xFFEC4899)) {
                        ExposedDropdownMenuBox(expanded = expandedTags, onExpandedChange = { expandedTags = it }) {
                            OutlinedTextField(
                                value = if (selectedTags.isEmpty()) "" else selectedTags.joinToString(", "),
                                onValueChange = {}, readOnly = true,
                                label = { Text("Select Tags") },
                                leadingIcon = { Icon(Icons.Default.Label, null, tint = Color(0xFFEC4899)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedTags) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                colors = modernTextFieldColors(), shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(expanded = expandedTags, onDismissRequest = { expandedTags = false }) {
                                allTags.forEach { tagItem ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Checkbox(checked = selectedTags.contains(tagItem), onCheckedChange = null)
                                                Text(tagItem)
                                            }
                                        },
                                        onClick = { selectedTags = if (selectedTags.contains(tagItem)) selectedTags - tagItem else selectedTags + tagItem }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Custom Fields Card
                if (customFields.isNotEmpty()) {
                    item {
                        ModernCard(title = "Custom Fields", icon = Icons.Default.DynamicForm, iconColor = Color(0xFFEC4899)) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                customFields.forEach { field ->
                                    CustomFieldInput(
                                        field = field,
                                        value = customFieldValues[field.id] ?: "",
                                        onValueChange = { newValue ->
                                            customFieldValues = customFieldValues + (field.id to newValue)
                                            customFieldErrors = customFieldErrors - field.id
                                        },
                                        isError = customFieldErrors.contains(field.id),
                                        errorMessage = if (customFieldErrors.contains(field.id)) "This field is required" else null
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Additional Info Card
                item {
                    ModernCard(title = "Additional Information", icon = Icons.Default.Info, iconColor = Color(0xFF10B981)) {
                        OutlinedTextField(
                            value = lastMessage, onValueChange = { lastMessage = it },
                            label = { Text("Last Message") },
                            leadingIcon = { Icon(Icons.Default.Message, null, tint = Color(0xFF10B981)) },
                            maxLines = 3, modifier = Modifier.fillMaxWidth(),
                            colors = modernTextFieldColors(), shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = notes, onValueChange = { notes = it },
                            label = { Text("Notes") },
                            leadingIcon = { Icon(Icons.Default.Notes, null, tint = Color(0xFF8B5CF6)) },
                            maxLines = 4, modifier = Modifier.fillMaxWidth(),
                            colors = modernTextFieldColors(), shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                
                // Save Button
                item {
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                // Validate required custom fields
                                val missingRequiredFields = customFields
                                    .filter { it.isRequired }
                                    .filter { customFieldValues[it.id].isNullOrBlank() }
                                    .map { it.id }
                                    .toSet()
                                
                                if (missingRequiredFields.isNotEmpty()) {
                                    customFieldErrors = missingRequiredFields
                                    return@Button
                                }
                                
                                val leadId = UUID.randomUUID().toString()
                                
                                // Create follow-up if scheduled
                                val followUps = if (scheduleFollowUp && followUpTitle.isNotBlank()) {
                                    val calendar = followUpDate.clone() as Calendar
                                    val timeParts = followUpTime.split(":")
                                    calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                                    calendar.set(Calendar.MINUTE, timeParts[1].toInt())
                                    
                                    listOf(FollowUp(
                                        id = UUID.randomUUID().toString(),
                                        leadId = leadId,
                                        title = followUpTitle,
                                        scheduledDate = calendar.timeInMillis,
                                        scheduledTime = followUpTime,
                                        type = followUpType
                                    ))
                                } else emptyList()
                                
                                val fullPhoneNumber = if (phoneNumber.isNotBlank()) {
                                    "${selectedCountry?.dialCode ?: ""}${phoneNumber.trim()}"
                                } else "N/A"
                                
                                val newLead = Lead(
                                    id = leadId,
                                    name = name.trim(),
                                    phoneNumber = fullPhoneNumber,
                                    email = email.trim(),
                                    countryCode = selectedCountry?.dialCode ?: "",
                                    countryIso = selectedCountry?.code ?: "",
                                    alternatePhone = alternatePhone.trim(),
                                    status = selectedStatus,
                                    source = selectedSource.ifEmpty { "Unknown" },
                                    lastMessage = lastMessage.trim(),
                                    timestamp = System.currentTimeMillis(),
                                    notes = notes.trim(),
                                    priority = selectedPriority,
                                    tags = selectedTags,
                                    product = selectedProduct,
                                    followUps = followUps,
                                    nextFollowUpDate = followUps.firstOrNull()?.scheduledDate
                                )
                                val leadAdded = leadManager.addLead(newLead)
                                
                                // Save custom field values
                                if (leadAdded && customFieldValues.isNotEmpty()) {
                                    coroutineScope.launch {
                                        customFieldsManager.saveFieldValues(leadId, customFieldValues)
                                    }
                                }
                                
                                if (leadAdded) {
                                    onLeadAdded()
                                } else {
                                    leadLimitMessage = "Free plan can add only 5 leads.\nUpgrade to Chatspromo Premium to add more."
                                    showLeadLimitDialog = true
                                }
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(64.dp).shadow(8.dp, RoundedCornerShape(16.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), disabledContainerColor = Color(0xFF64748B)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Save Lead", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    if (showLeadLimitDialog) {
        AlertDialog(
            onDismissRequest = { showLeadLimitDialog = false },
            title = {
                Text(
                    "Lead Limit Reached",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    leadLimitMessage,
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        context.startActivity(Intent(context, PrepackActivity::class.java))
                        showLeadLimitDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Upgrade")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeadLimitDialog = false }) {
                    Text("OK", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1a1a2e)
        )
    }
}

@Composable
fun ModernCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(iconColor.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp)) }
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            content()
        }
    }
}

@Composable
fun modernTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF10B981),
    focusedLabelColor = Color(0xFF10B981),
    unfocusedBorderColor = Color(0xFF64748B),
    unfocusedLabelColor = Color(0xFF94A3B8),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    errorTextColor = Color.White,
    cursorColor = Color(0xFF10B981),
    errorCursorColor = Color(0xFFEF4444),
    focusedLeadingIconColor = Color(0xFF10B981),
    unfocusedLeadingIconColor = Color(0xFF94A3B8),
    errorLeadingIconColor = Color(0xFFEC4899)
)
