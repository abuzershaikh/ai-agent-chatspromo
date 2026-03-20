package com.message.bulksend.leadmanager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.activity.compose.BackHandler

@Composable
fun ProductManagementScreen(
    products: List<Product>,
    onBack: () -> Unit,
    onAddProduct: () -> Unit,
    onProductClick: (Product) -> Unit,
    onOpenSettings: () -> Unit
) {
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
                    
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Product Management",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "${products.size} products",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    
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
            
            // Product List
            if (products.isEmpty()) {
                EmptyProductState()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(products) { product ->
                        ProductCard(
                            product = product,
                            onClick = { onProductClick(product) }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
        
        // FAB
        FloatingActionButton(
            onClick = onAddProduct,
            containerColor = Color(0xFFF59E0B),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 80.dp, end = 16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Product", tint = Color.White)
        }
    }
}

@Composable
fun ProductCard(product: Product, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(product.type.color).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (product.type) {
                            ProductType.PHYSICAL -> Icons.Default.Inventory
                            ProductType.DIGITAL -> Icons.Default.CloudDownload
                            ProductType.SERVICE -> Icons.Default.Handshake
                            ProductType.SOFTWARE -> Icons.Default.Code
                        },
                        contentDescription = null,
                        tint = Color(product.type.color),
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        product.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        product.type.displayName,
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8)
                    )
                    if (product.sellingPrice.isNotEmpty()) {
                        Text(
                            "₹${product.sellingPrice}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF64748B)
            )
        }
    }
}

@Composable
fun EmptyProductState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Inventory,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(64.dp)
            )
            Text(
                "No products yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8)
            )
            Text(
                "Add your first product to get started",
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}
