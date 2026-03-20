package com.message.bulksend.leadmanager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.message.bulksend.leadmanager.model.Product
import com.message.bulksend.leadmanager.model.ProductType
import com.message.bulksend.leadmanager.model.ServiceType
import com.message.bulksend.leadmanager.model.ProductFieldSettings
import java.util.UUID
import androidx.activity.compose.BackHandler

@Composable
fun AddEditProductScreen(
    product: Product? = null,
    fieldSettings: ProductFieldSettings,
    onBack: () -> Unit,
    onSave: (Product) -> Unit,
    onOpenSettings: () -> Unit
) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var selectedType by remember { mutableStateOf(product?.type ?: ProductType.PHYSICAL) }
    var category by remember { mutableStateOf(product?.category ?: "") }
    var subcategory by remember { mutableStateOf(product?.subcategory ?: "") }
    var mrp by remember { mutableStateOf(product?.mrp ?: "") }
    var sellingPrice by remember { mutableStateOf(product?.sellingPrice ?: "") }
    var description by remember { mutableStateOf(product?.description ?: "") }
    
    // Physical Product Fields
    var color by remember { mutableStateOf(product?.color ?: "") }
    var size by remember { mutableStateOf(product?.size ?: "") }
    var height by remember { mutableStateOf(product?.height ?: "") }
    var width by remember { mutableStateOf(product?.width ?: "") }
    var weight by remember { mutableStateOf(product?.weight ?: "") }
    
    // Digital Product Fields
    var downloadLink by remember { mutableStateOf(product?.downloadLink ?: "") }
    var licenseType by remember { mutableStateOf(product?.licenseType ?: "") }
    var version by remember { mutableStateOf(product?.version ?: "") }
    
    // Service Fields
    var serviceType by remember { mutableStateOf(product?.serviceType ?: ServiceType.ONLINE) }
    var duration by remember { mutableStateOf(product?.duration ?: "") }
    var deliveryTime by remember { mutableStateOf(product?.deliveryTime ?: "") }
    
    var showTypeMenu by remember { mutableStateOf(false) }
    var showServiceTypeMenu by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    
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
                            .background(Color(0xFFF59E0B).copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFF59E0B)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        if (product == null) "Add Product" else "Edit Product",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Field Settings",
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Product Type Selection
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
                                color = Color(0xFFF59E0B)
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
                
                // Basic Information
                item {
                    SectionCard(
                        title = "Basic Information",
                        icon = Icons.Default.Info,
                        color = Color(0xFF3B82F6)
                    ) {
                        ProductTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = "Product Name *",
                            icon = Icons.Default.Label,
                            isError = showError && name.isEmpty()
                        )
                        
                        if (fieldSettings.isFieldEnabled(selectedType, "category")) {
                            ProductTextField(
                                value = category,
                                onValueChange = { category = it },
                                label = "Category",
                                icon = Icons.Default.Category
                            )
                        }
                        
                        if (fieldSettings.isFieldEnabled(selectedType, "subcategory")) {
                            ProductTextField(
                                value = subcategory,
                                onValueChange = { subcategory = it },
                                label = "Subcategory",
                                icon = Icons.Default.SubdirectoryArrowRight
                            )
                        }
                    }
                }
                
                // Pricing
                item {
                    SectionCard(
                        title = "Pricing",
                        icon = Icons.Default.AccountBalanceWallet,
                        color = Color(0xFF10B981)
                    ) {
                        if (fieldSettings.isFieldEnabled(selectedType, "mrp")) {
                            ProductTextField(
                                value = mrp,
                                onValueChange = { mrp = it },
                                label = "MRP",
                                icon = Icons.Default.AccountBalanceWallet
                            )
                        }
                        
                        if (fieldSettings.isFieldEnabled(selectedType, "sellingPrice")) {
                            ProductTextField(
                                value = sellingPrice,
                                onValueChange = { sellingPrice = it },
                                label = "Selling Price",
                                icon = Icons.Default.Sell
                            )
                        }
                    }
                }
                
                // Type-Specific Fields
                when (selectedType) {
                    ProductType.PHYSICAL -> {
                        item {
                            SectionCard(
                                title = "Physical Product Details",
                                icon = Icons.Default.Inventory,
                                color = Color(0xFF3B82F6)
                            ) {
                                if (fieldSettings.isFieldEnabled(selectedType, "color")) {
                                    ProductTextField(
                                        value = color,
                                        onValueChange = { color = it },
                                        label = "Color",
                                        icon = Icons.Default.Palette
                                    )
                                }
                                
                                if (fieldSettings.isFieldEnabled(selectedType, "size")) {
                                    ProductTextField(
                                        value = size,
                                        onValueChange = { size = it },
                                        label = "Size",
                                        icon = Icons.Default.Straighten
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (fieldSettings.isFieldEnabled(selectedType, "height")) {
                                        ProductTextField(
                                            value = height,
                                            onValueChange = { height = it },
                                            label = "Height (cm)",
                                            icon = Icons.Default.Height,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    
                                    if (fieldSettings.isFieldEnabled(selectedType, "width")) {
                                        ProductTextField(
                                            value = width,
                                            onValueChange = { width = it },
                                            label = "Width (cm)",
                                            icon = Icons.Default.Straighten,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                
                                if (fieldSettings.isFieldEnabled(selectedType, "weight")) {
                                    ProductTextField(
                                        value = weight,
                                        onValueChange = { weight = it },
                                        label = "Weight (grams)",
                                        icon = Icons.Default.FitnessCenter
                                    )
                                }
                            }
                        }
                    }
                    
                    ProductType.DIGITAL -> {
                        item {
                            SectionCard(
                                title = "Digital Product Details",
                                icon = Icons.Default.CloudDownload,
                                color = Color(0xFF10B981)
                            ) {
                                if (fieldSettings.isFieldEnabled(selectedType, "downloadLink")) {
                                    ProductTextField(
                                        value = downloadLink,
                                        onValueChange = { downloadLink = it },
                                        label = "Download Link",
                                        icon = Icons.Default.Link
                                    )
                                }
                                
                                if (fieldSettings.isFieldEnabled(selectedType, "licenseType")) {
                                    ProductTextField(
                                        value = licenseType,
                                        onValueChange = { licenseType = it },
                                        label = "License Type",
                                        icon = Icons.Default.Key
                                    )
                                }
                                
                                if (fieldSettings.isFieldEnabled(selectedType, "version")) {
                                    ProductTextField(
                                        value = version,
                                        onValueChange = { version = it },
                                        label = "Version",
                                        icon = Icons.Default.Update
                                    )
                                }
                            }
                        }
                    }
                    
                    ProductType.SERVICE -> {
                        item {
                            SectionCard(
                                title = "Service Details",
                                icon = Icons.Default.Handshake,
                                color = Color(0xFFF59E0B)
                            ) {
                                if (fieldSettings.isFieldEnabled(selectedType, "serviceType")) {
                                    Text(
                                        "Service Type",
                                        fontSize = 14.sp,
                                        color = Color(0xFF94A3B8),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ServiceType.values().forEach { type ->
                                            ServiceTypeChip(
                                                type = type,
                                                isSelected = serviceType == type,
                                                onClick = { serviceType = type },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                
                                if (fieldSettings.isFieldEnabled(selectedType, "duration")) {
                                    ProductTextField(
                                        value = duration,
                                        onValueChange = { duration = it },
                                        label = "Duration",
                                        icon = Icons.Default.Schedule
                                    )
                                }
                                
                                if (fieldSettings.isFieldEnabled(selectedType, "deliveryTime")) {
                                    ProductTextField(
                                        value = deliveryTime,
                                        onValueChange = { deliveryTime = it },
                                        label = "Delivery Time",
                                        icon = Icons.Default.DeliveryDining
                                    )
                                }
                            }
                        }
                    }
                    
                    ProductType.SOFTWARE -> {
                        item {
                            SectionCard(
                                title = "Software Details",
                                icon = Icons.Default.Code,
                                color = Color(0xFF8B5CF6)
                            ) {
                                if (fieldSettings.isFieldEnabled(selectedType, "version")) {
                                    ProductTextField(
                                        value = version,
                                        onValueChange = { version = it },
                                        label = "Version",
                                        icon = Icons.Default.Update
                                    )
                                }
                                
                                if (fieldSettings.isFieldEnabled(selectedType, "licenseType")) {
                                    ProductTextField(
                                        value = licenseType,
                                        onValueChange = { licenseType = it },
                                        label = "License Type",
                                        icon = Icons.Default.Key
                                    )
                                }
                                
                                if (fieldSettings.isFieldEnabled(selectedType, "downloadLink")) {
                                    ProductTextField(
                                        value = downloadLink,
                                        onValueChange = { downloadLink = it },
                                        label = "Download/Access Link",
                                        icon = Icons.Default.Link
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Description
                item {
                    SectionCard(
                        title = "Description",
                        icon = Icons.Default.Description,
                        color = Color(0xFF64748B)
                    ) {
                        if (fieldSettings.isFieldEnabled(selectedType, "description")) {
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF64748B),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color(0xFF94A3B8),
                                    cursorColor = Color(0xFF64748B)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                placeholder = {
                                    Text("Enter product description...", color = Color(0xFF64748B))
                                }
                            )
                        }
                    }
                }
                
                // Save Button
                item {
                    Button(
                        onClick = {
                            if (name.isEmpty()) {
                                showError = true
                            } else {
                                val newProduct = Product(
                                    id = product?.id ?: UUID.randomUUID().toString(),
                                    name = name,
                                    type = selectedType,
                                    category = category,
                                    subcategory = subcategory,
                                    mrp = mrp,
                                    sellingPrice = sellingPrice,
                                    description = description,
                                    color = color,
                                    size = size,
                                    height = height,
                                    width = width,
                                    weight = weight,
                                    downloadLink = downloadLink,
                                    licenseType = licenseType,
                                    version = version,
                                    serviceType = serviceType,
                                    duration = duration,
                                    deliveryTime = deliveryTime
                                )
                                onSave(newProduct)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (product == null) "Add Product" else "Update Product",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
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
fun ProductTypeChip(
    type: ProductType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(type.color) else Color(0xFF0f172a)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                when (type) {
                    ProductType.PHYSICAL -> Icons.Default.Inventory
                    ProductType.DIGITAL -> Icons.Default.CloudDownload
                    ProductType.SERVICE -> Icons.Default.Handshake
                    ProductType.SOFTWARE -> Icons.Default.Code
                },
                contentDescription = null,
                tint = if (isSelected) Color.White else Color(type.color),
                modifier = Modifier.size(32.dp)
            )
            Text(
                type.displayName,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
fun ServiceTypeChip(
    type: ServiceType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFF59E0B) else Color(0xFF0f172a)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            type.displayName.split(" ").first(),
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else Color(0xFF94A3B8),
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
            
            content()
        }
    }
}

@Composable
fun ProductTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (isError) Color(0xFFEF4444) else Color(0xFFF59E0B),
            unfocusedBorderColor = if (isError) Color(0xFFEF4444) else Color(0xFF334155),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color(0xFF94A3B8),
            focusedLabelColor = if (isError) Color(0xFFEF4444) else Color(0xFFF59E0B),
            unfocusedLabelColor = Color(0xFF64748B),
            focusedLeadingIconColor = if (isError) Color(0xFFEF4444) else Color(0xFFF59E0B),
            unfocusedLeadingIconColor = Color(0xFF64748B),
            cursorColor = Color(0xFFF59E0B)
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        isError = isError
    )
}
