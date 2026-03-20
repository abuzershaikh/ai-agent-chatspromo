package com.message.bulksend.aiagent

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.aiagent.screens.AIAgentSettingsScreen
import com.message.bulksend.aiagent.screens.AITemplatesScreen
import com.message.bulksend.aiagent.screens.AgentHomeScreen
import com.message.bulksend.ui.theme.BulksendTestTheme

class AIAgentDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(scrim = AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(scrim = AndroidColor.TRANSPARENT)
        )

        setContent { BulksendTestTheme { AIAgentDashboardScreen(onBackPressed = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAgentDashboardScreen(onBackPressed: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        DottedCanvasBackground(modifier = Modifier.matchParentSize())

        Scaffold(
                topBar = { AIAgentTopBar(selectedTab = selectedTab, onBackPressed = onBackPressed) },
                bottomBar = {
                    AIAgentBottomNavigation(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it }
                    )
                },
                containerColor = Color.Transparent
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (selectedTab) {
                    0 -> AgentHomeScreen()
                    1 -> AITemplatesScreen()
                    2 -> AIAgentSettingsScreen()
                }
            }
        }
    }
}

@Composable
fun DottedCanvasBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // n8n-style dark canvas base
        drawRect(
                brush =
                        Brush.verticalGradient(
                                colors = listOf(Color(0xFF0B0F17), Color(0xFF121826))
                        )
        )
        drawRect(
                brush =
                        Brush.radialGradient(
                                colors =
                                        listOf(
                                                Color(0xFF1E9B8F).copy(alpha = 0.08f),
                                                Color.Transparent
                                        ),
                                center = Offset(size.width * 0.1f, size.height * 0.85f),
                                radius = size.minDimension * 0.75f
                        )
        )

        val dotRadius = 2.0f
        val spacing = 26f
        val dotColor = Color(0xFF2F3A4D).copy(alpha = 0.7f)

        val columns = (size.width / spacing).toInt() + 2
        val rows = (size.height / spacing).toInt() + 1

        for (i in 0 until columns) {
            for (j in 0 until rows) {
                val xOffset = if (j % 2 == 0) 0f else spacing / 2f
                drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(i * spacing + xOffset, j * spacing)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAgentTopBar(selectedTab: Int, onBackPressed: () -> Unit) {
    val title =
            when (selectedTab) {
                0 -> "Agent Tool"
                1 -> "AI Templates"
                2 -> "AI Settings"
                else -> "AI Agent"
            }

    Column(modifier = Modifier.fillMaxWidth().background(Color.Transparent)) {
        Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))

        Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onBackPressed) {
                    Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                    )
                }

                Icon(
                        Icons.Outlined.SmartToy,
                        contentDescription = "AI Agent",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(28.dp)
                )

                Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun AIAgentBottomNavigation(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs =
            listOf(
                    BottomNavItem(Icons.Outlined.Home, "Agent Home", Color(0xFFFF6B6B)),
                    BottomNavItem(Icons.Outlined.Dashboard, "Templates", Color(0xFF10B981)),
                    BottomNavItem(Icons.Outlined.Settings, "Settings", Color(0xFF6366F1))
            )

    Box(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .height(80.dp)
                                .background(
                                        color = Color.Transparent,
                                        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
                                )
        ) {
            Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, item ->
                    AIAgentBottomNavItem(
                            item = item,
                            isSelected = selectedTab == index,
                            onClick = { onTabSelected(index) }
                    )
                }
            }
        }
    }
}

data class BottomNavItem(val icon: ImageVector, val label: String, val color: Color)

@Composable
fun AIAgentBottomNavItem(item: BottomNavItem, isSelected: Boolean, onClick: () -> Unit) {
    val scale by
            animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1f,
                    animationSpec =
                            spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                            ),
                    label = "tab_scale"
            )

    val iconScale by
            animateFloatAsState(
                    targetValue = if (isSelected) 1.2f else 1f,
                    animationSpec =
                            spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                            ),
                    label = "icon_scale"
            )

    Card(
            modifier = Modifier.width(85.dp).height(62.dp).scale(scale),
            shape = RoundedCornerShape(16.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    if (isSelected) item.color.copy(alpha = 0.2f)
                                    else Color(0xFF2A2A2A)
                    ),
            elevation =
                    CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .clickable(
                                        onClick = onClick,
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                )
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    modifier = Modifier.size(22.dp).scale(iconScale),
                    tint = if (isSelected) item.color else Color(0xFF808080)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                    text = item.label,
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.White else Color(0xFF808080),
                    maxLines = 1,
                    textAlign = TextAlign.Center
            )
        }
    }
}
