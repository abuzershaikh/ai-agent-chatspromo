package com.message.bulksend.autorespond.ai.ui.customai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Shared palette ─────────────────────────────────────────────────────────
private val CompBgCard    = Color(0xFF111827)
private val CompBgAlt     = Color(0xFF0F172A)
private val CompBorder    = Color(0xFF1E293B)
private val CompText      = Color.White
private val CompTextSub   = Color(0xFF94A3B8)
private val CompDefaultAccent = Color(0xFF38BDF8)

@Composable
internal fun ToolSetupActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    accentColor: Color = CompDefaultAccent,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled)
                    Brush.linearGradient(listOf(CompBgCard, CompBgAlt))
                else
                    Brush.linearGradient(listOf(Color(0xFF1C2433), Color(0xFF111827)))
            )
            .border(
                width = 1.dp,
                color = if (enabled) accentColor.copy(0.35f) else CompBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (enabled) accentColor.copy(0.15f) else Color(0xFF1E293B)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) accentColor else CompTextSub,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (enabled) CompText else CompTextSub,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = CompTextSub,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Start
                )
            }
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = null,
                tint = if (enabled) accentColor.copy(0.6f) else CompBorder,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun ToolToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    accentColor: Color = CompDefaultAccent
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (checked && enabled)
                    accentColor.copy(0.08f)
                else
                    CompBgAlt
            )
            .border(
                width = 1.dp,
                color = if (checked && enabled) accentColor.copy(0.4f) else CompBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (checked && enabled)
                            accentColor.copy(0.2f)
                        else
                            Color(0xFF1E293B)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (checked && enabled) accentColor else CompTextSub,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (enabled) CompText else CompTextSub,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = CompTextSub,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor   = Color.White,
                    checkedTrackColor   = accentColor,
                    uncheckedThumbColor = Color(0xFF64748B),
                    uncheckedTrackColor = Color(0xFF1E293B),
                    uncheckedBorderColor= Color(0xFF334155)
                )
            )
        }
    }
}

@Composable
internal fun HorizontalToolToggleCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    accentColor: Color = Color(0xFF8B5CF6)
) {
    Box(
        modifier = modifier
            .height(196.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (checked && enabled)
                    accentColor.copy(0.1f)
                else
                    Color(0xFF111827)
            )
            .border(
                width = if (checked && enabled) 1.5.dp else 1.dp,
                color = if (checked && enabled) accentColor.copy(0.5f) else CompBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(if (checked && enabled) 0.25f else 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (enabled) accentColor else CompTextSub
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (checked) "Enabled" else "Disabled",
                    color = if (checked && enabled) accentColor else CompTextSub,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                title,
                color = if (enabled) CompText else CompTextSub,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                subtitle,
                color = CompTextSub,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.weight(1f))
            HorizontalDivider(color = CompBorder, thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Allow tool",
                    color = if (enabled) CompText.copy(0.82f) else CompTextSub,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = Color.White,
                        checkedTrackColor   = accentColor,
                        uncheckedThumbColor = Color(0xFF64748B),
                        uncheckedTrackColor = Color(0xFF1E293B),
                        uncheckedBorderColor= Color(0xFF334155)
                    )
                )
            }
        }
    }
}
