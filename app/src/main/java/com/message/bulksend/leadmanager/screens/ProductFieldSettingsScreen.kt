package com.message.bulksend.leadmanager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.model.ProductType
import com.message.bulksend.leadmanager.model.ProductFieldSettings
import androidx.activity.compose.BackHandler

@Composable
fun ProductFieldSettingsScreen(
    fieldSettings: ProductFieldSettings,
    onBack: () -> Unit,
    onSettingsChange: (ProductFieldSettings) -> Unit
) {
    var selectedType by remember { mutableStateOf(ProductType.PHYSICAL) }
    var currentSettings by remember { mutableStateOf(fieldSettings) }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    // Handle back press
    BackHandler { onBack() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1a1a2e),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF8B5CF6)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Field Settings",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Customize fields",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Product Type Selector
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Select Product Type",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8B5CF6)
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ProductType.values().forEach { type ->
                                    ProductTypeChip(
                                        type = type,
                                        isSelected = selectedType == type,
                                        onClick = { selectedType = type }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Common Fields
                item {
                    FieldSettingsSection(
                        title = "Common Fields",
                        icon = Icons.Default.Info,
                        color = Color(0xFF3B82F6),
                        fields = listOf(
                            FieldItem("category", "Category", Icons.Default.Category),
                            FieldItem("subcategory", "Subcategory", Icons.Default.SubdirectoryArrowRight),
                            FieldItem("mrp", "MRP", Icons.Default.CurrencyRupee),
                            FieldItem("sellingPrice", "Selling Price", Icons.Default.Sell),
                            FieldItem("description", "Description", Icons.Default.Description)
                        ),
                        currentSettings = currentSettings,
                        selectedType = selectedType,
                        onToggle = { fieldName, enabled ->
                            currentSettings = currentSettings.toggleField(selectedType, fieldName, enabled)
                            onSettingsChange(currentSettings)
                        }
                    )
                }
                
                // Type-Specific Fields
                when (selectedType) {
                    ProductType.PHYSICAL -> {
                        item {
                            FieldSettingsSection(
                                title = "Physical Product Fields",
                                icon = Icons.Default.Inventory,
                                color = Color(0xFF3B82F6),
                                fields = listOf(
                                    FieldItem("color", "Color", Icons.Default.Palette),
                                    FieldItem("size", "Size", Icons.Default.Straighten),
                                    FieldItem("height", "Height", Icons.Default.Height),
                                    FieldItem("width", "Width", Icons.Default.Straighten),
                                    FieldItem("weight", "Weight", Icons.Default.FitnessCenter)
                                ),
                                currentSettings = currentSettings,
                                selectedType = selectedType,
                                onToggle = { fieldName, enabled ->
                                    currentSettings = currentSettings.toggleField(selectedType, fieldName, enabled)
                                    onSettingsChange(currentSettings)
                                }
                            )
                        }
                    }
                    
                    ProductType.DIGITAL -> {
                        item {
                            FieldSettingsSection(
                                title = "Digital Product Fields",
                                icon = Icons.Default.CloudDownload,
                                color = Color(0xFF10B981),
                                fields = listOf(
                                    FieldItem("downloadLink", "Download Link", Icons.Default.Link),
                                    FieldItem("licenseType", "License Type", Icons.Default.Key),
                                    FieldItem("version", "Version", Icons.Default.Update)
                                ),
                                currentSettings = currentSettings,
                                selectedType = selectedType,
                                onToggle = { fieldName, enabled ->
                                    currentSettings = currentSettings.toggleField(selectedType, fieldName, enabled)
                                    onSettingsChange(currentSettings)
                                }
                            )
                        }
                    }
                    
                    ProductType.SERVICE -> {
                        item {
                            FieldSettingsSection(
                                title = "Service Fields",
                                icon = Icons.Default.Handshake,
                                color = Color(0xFFF59E0B),
                                fields = listOf(
                                    FieldItem("serviceType", "Service Type", Icons.Default.Category),
                                    FieldItem("duration", "Duration", Icons.Default.Schedule),
                                    FieldItem("deliveryTime", "Delivery Time", Icons.Default.DeliveryDining)
                                ),
                                currentSettings = currentSettings,
                                selectedType = selectedType,
                                onToggle = { fieldName, enabled ->
                                    currentSettings = currentSettings.toggleField(selectedType, fieldName, enabled)
                                    onSettingsChange(currentSettings)
                                }
                            )
                        }
                    }
                    
                    ProductType.SOFTWARE -> {
                        item {
                            FieldSettingsSection(
                                title = "Software Fields",
                                icon = Icons.Default.Code,
                                color = Color(0xFF8B5CF6),
                                fields = listOf(
                                    FieldItem("version", "Version", Icons.Default.Update),
                                    FieldItem("licenseType", "License Type", Icons.Default.Key),
                                    FieldItem("downloadLink", "Download/Access Link", Icons.Default.Link)
                                ),
                                currentSettings = currentSettings,
                                selectedType = selectedType,
                                onToggle = { fieldName, enabled ->
                                    currentSettings = currentSettings.toggleField(selectedType, fieldName, enabled)
                                    onSettingsChange(currentSettings)
                                }
                            )
                        }
                    }
                }
                
                // Info Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(24.dp)
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "About Field Settings",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF60A5FA)
                                )
                                Text(
                                    "Enable or disable fields for each product type. Disabled fields won't appear in the Add/Edit Product form.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
fun FieldSettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    fields: List<FieldItem>,
    currentSettings: ProductFieldSettings,
    selectedType: ProductType,
    onToggle: (String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            
            fields.forEach { field ->
                FieldToggleItem(
                    field = field,
                    isEnabled = currentSettings.isFieldEnabled(selectedType, field.name),
                    onToggle = { enabled -> onToggle(field.name, enabled) }
                )
            }
        }
    }
}

@Composable
fun FieldToggleItem(
    field: FieldItem,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0f172a),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    field.icon,
                    contentDescription = null,
                    tint = if (isEnabled) Color(0xFFF59E0B) else Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    field.displayName,
                    fontSize = 14.sp,
                    color = if (isEnabled) Color.White else Color(0xFF64748B)
                )
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFFF59E0B),
                    uncheckedThumbColor = Color(0xFF64748B),
                    uncheckedTrackColor = Color(0xFF1e293b)
                )
            )
        }
    }
}

data class FieldItem(
    val name: String,
    val displayName: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
