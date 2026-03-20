package com.message.bulksend.leadmanager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.message.bulksend.leadmanager.model.Lead
import com.message.bulksend.leadmanager.model.LeadStatus

@Composable
fun CategoriesScreen(leads: List<Lead>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Categories",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(LeadStatus.values()) { status ->
                val count = leads.count { it.status == status }
                CategoryCard(
                    status = status,
                    count = count
                )
            }
        }
    }
}

@Composable
fun CategoryCard(status: LeadStatus, count: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(status.color).copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(status.color).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (status) {
                        LeadStatus.NEW -> Icons.Default.FiberNew
                        LeadStatus.INTERESTED -> Icons.Default.Favorite
                        LeadStatus.CONTACTED -> Icons.Default.Phone
                        LeadStatus.QUALIFIED -> Icons.Default.CheckCircle
                        LeadStatus.CONVERTED -> Icons.Default.TrendingUp
                        LeadStatus.CUSTOMER -> Icons.Default.Person
                        LeadStatus.LOST -> Icons.Default.Cancel
                    },
                    contentDescription = null,
                    tint = Color(status.color),
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    count.toString(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    status.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(status.color),
                    maxLines = 1
                )
            }
        }
    }
}
