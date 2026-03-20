package com.message.bulksend.aiagent.tools.shipping

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BuildCircle
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.aiagent.tools.delhivery.DelhiverySetupActivity
import com.message.bulksend.ui.theme.BulksendTestTheme

class CourierShippingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BulksendTestTheme {
                CourierShippingScreen(onBackPressed = { finish() })
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CourierShippingScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F0C29),
            Color(0xFF1B1640),
            Color(0xFF24243E),
            Color(0xFF0F0C29)
        )
    )

    var shiprocketEnabled by remember { mutableStateOf(false) }
    var delhiveryEnabled by remember { mutableStateOf(false) }
    var comingSoonEnabled by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Courier Shipping",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Choose Courier Partner",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF22D3EE),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                item {
                    CourierProviderCard(
                        icon = Icons.Outlined.Speed,
                        title = "Shiprocket Courier Shipping",
                        subtitle = "Configure Shiprocket courier tools",
                        color = Color(0xFF6366F1),
                        checked = shiprocketEnabled,
                        onCheckedChange = { shiprocketEnabled = it },
                        onClick = {
                            context.startActivity(Intent(context, ShiprocketSetupActivity::class.java))
                        }
                    )
                }

                item {
                    CourierProviderCard(
                        icon = Icons.Outlined.LocalShipping,
                        title = "Delhivery",
                        subtitle = "Configure Delhivery courier tools",
                        color = Color(0xFF10B981),
                        checked = delhiveryEnabled,
                        onCheckedChange = { delhiveryEnabled = it },
                        onClick = {
                            context.startActivity(Intent(context, DelhiverySetupActivity::class.java))
                        }
                    )
                }

                item {
                    CourierProviderCard(
                        icon = Icons.Outlined.BuildCircle,
                        title = "More Courier Coming Soon",
                        subtitle = "Additional courier integrations will be added",
                        color = Color(0xFFF59E0B),
                        checked = comingSoonEnabled,
                        onCheckedChange = { comingSoonEnabled = it },
                        onClick = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun CourierProviderCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .border(
                        width = 1.dp,
                        color = color.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color
                )
            }

            Spacer(modifier = Modifier.size(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = color,
                    checkedTrackColor = color.copy(alpha = 0.45f),
                    uncheckedThumbColor = Color(0xFF94A3B8),
                    uncheckedTrackColor = Color(0xFF334155)
                )
            )
        }
    }
}
