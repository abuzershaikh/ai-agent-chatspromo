package com.message.bulksend.leadmanager.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.LeadManager
import androidx.activity.compose.BackHandler

@Composable
fun ManagementScreen(
    leadManager: LeadManager,
    type: ManagementType,
    onBack: () -> Unit
) {
    // For Products, show the new ProductManagementScreen
    if (type == ManagementType.PRODUCTS) {
        var showAddEditScreen by remember { mutableStateOf(false) }
        var showSettingsScreen by remember { mutableStateOf(false) }
        var selectedProduct by remember { mutableStateOf<com.message.bulksend.leadmanager.model.Product?>(null) }
        var products by remember { mutableStateOf(leadManager.getAllProductsV2()) }
        var fieldSettings by remember { mutableStateOf(leadManager.getProductFieldSettings()) }
        
        when {
            showSettingsScreen -> {
                ProductFieldSettingsScreen(
                    fieldSettings = fieldSettings,
                    onBack = { showSettingsScreen = false },
                    onSettingsChange = { newSettings ->
                        fieldSettings = newSettings
                        leadManager.saveProductFieldSettings(newSettings)
                    }
                )
            }
            showAddEditScreen -> {
                AddEditProductScreen(
                    product = selectedProduct,
                    fieldSettings = fieldSettings,
                    onBack = {
                        showAddEditScreen = false
                        selectedProduct = null
                    },
                    onSave = { product ->
                        if (selectedProduct == null) {
                            leadManager.addProductV2(product)
                        } else {
                            leadManager.updateProductV2(product)
                        }
                        products = leadManager.getAllProductsV2()
                        showAddEditScreen = false
                        selectedProduct = null
                    },
                    onOpenSettings = {
                        showAddEditScreen = false
                        showSettingsScreen = true
                    }
                )
            }
            else -> {
                ProductManagementScreen(
                    products = products,
                    onBack = onBack,
                    onAddProduct = { showAddEditScreen = true },
                    onProductClick = { product ->
                        selectedProduct = product
                        showAddEditScreen = true
                    },
                    onOpenSettings = { showSettingsScreen = true }
                )
            }
        }
        return
    }
    
    // For Tags and Sources, use the old simple management
    var showAddDialog by remember { mutableStateOf(false) }
    var items by remember { 
        mutableStateOf(
            when (type) {
                ManagementType.TAGS -> leadManager.getAllTags()
                ManagementType.SOURCES -> leadManager.getAllSources()
                ManagementType.PRODUCTS -> leadManager.getAllProducts()
            }
        )
    }
    
    // Handle back press
    BackHandler { onBack() }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    "Manage ${type.displayName}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (items.isEmpty()) {
                EmptyManagementState(type)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items) { item ->
                        ManagementItemCard(
                            item = item,
                            type = type,
                            onDelete = {
                                when (type) {
                                    ManagementType.TAGS -> leadManager.deleteTag(item)
                                    ManagementType.SOURCES -> leadManager.deleteSource(item)
                                    ManagementType.PRODUCTS -> leadManager.deleteProduct(item)
                                }
                                items = when (type) {
                                    ManagementType.TAGS -> leadManager.getAllTags()
                                    ManagementType.SOURCES -> leadManager.getAllSources()
                                    ManagementType.PRODUCTS -> leadManager.getAllProducts()
                                }
                            }
                        )
                    }
                }
            }
        }
        
        // FAB at bottom right with navigation bar padding
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = type.color,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 80.dp, end = 16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
        }
    }
    
    if (showAddDialog) {
        AddItemDialog(
            type = type,
            onDismiss = { showAddDialog = false },
            onAdd = { newItem ->
                when (type) {
                    ManagementType.TAGS -> leadManager.addTag(newItem)
                    ManagementType.SOURCES -> leadManager.addSource(newItem)
                    ManagementType.PRODUCTS -> leadManager.addProduct(newItem)
                }
                items = when (type) {
                    ManagementType.TAGS -> leadManager.getAllTags()
                    ManagementType.SOURCES -> leadManager.getAllSources()
                    ManagementType.PRODUCTS -> leadManager.getAllProducts()
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ManagementItemCard(
    item: String,
    type: ManagementType,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    type.icon,
                    contentDescription = null,
                    tint = type.color,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    item,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFEF4444)
                )
            }
        }
    }
}

@Composable
fun EmptyManagementState(type: ManagementType) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                type.icon,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(64.dp)
            )
            Text(
                "No ${type.displayName.lowercase()} yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8)
            )
            Text(
                "Add your first ${type.displayName.lowercase().dropLast(1)} to get started",
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
fun AddItemDialog(
    type: ManagementType,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add ${type.displayName.dropLast(1)}")
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("${type.displayName.dropLast(1)} name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onAdd(text.trim())
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

enum class ManagementType(
    val displayName: String,
    val icon: ImageVector,
    val color: Color
) {
    TAGS("Tags", Icons.Default.Label, Color(0xFF10B981)),
    SOURCES("Sources", Icons.Default.Source, Color(0xFF3B82F6)),
    PRODUCTS("Products", Icons.Default.Inventory, Color(0xFFF59E0B))
}
