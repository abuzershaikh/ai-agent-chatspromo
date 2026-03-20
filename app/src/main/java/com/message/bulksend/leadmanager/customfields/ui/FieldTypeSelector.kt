package com.message.bulksend.leadmanager.customfields.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.database.entities.CustomFieldType

/**
 * Data class representing a field type option with its display properties
 */
data class FieldTypeOption(
    val type: CustomFieldType,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val iconTint: Color
)

/**
 * Get all field type options with their display properties
 * Requirements: 2.1-2.12
 */
fun getFieldTypeOptions(): List<FieldTypeOption> = listOf(
    FieldTypeOption(
        type = CustomFieldType.TEXT,
        name = "Text",
        description = "Single line text input",
        icon = Icons.Default.TextFields,
        iconTint = Color(0xFF3B82F6) // Blue
    ),
    FieldTypeOption(
        type = CustomFieldType.NUMBER,
        name = "Number",
        description = "Numeric input only",
        icon = Icons.Default.Numbers,
        iconTint = Color(0xFF10B981) // Green
    ),
    FieldTypeOption(
        type = CustomFieldType.DATE,
        name = "Date",
        description = "Date picker selection",
        icon = Icons.Default.DateRange,
        iconTint = Color(0xFFF59E0B) // Amber
    ),
    FieldTypeOption(
        type = CustomFieldType.TIME,
        name = "Time",
        description = "Time picker selection",
        icon = Icons.Default.Schedule,
        iconTint = Color(0xFF8B5CF6) // Purple
    ),
    FieldTypeOption(
        type = CustomFieldType.DATETIME,
        name = "Date & Time",
        description = "Date and time picker",
        icon = Icons.Default.Event,
        iconTint = Color(0xFFEC4899) // Pink
    ),
    FieldTypeOption(
        type = CustomFieldType.DROPDOWN,
        name = "Dropdown",
        description = "Select from options",
        icon = Icons.Default.ArrowDropDownCircle,
        iconTint = Color(0xFF06B6D4) // Cyan
    ),
    FieldTypeOption(
        type = CustomFieldType.CHECKBOX,
        name = "Checkbox",
        description = "Yes/No toggle switch",
        icon = Icons.Default.CheckBox,
        iconTint = Color(0xFF22C55E) // Green
    ),
    FieldTypeOption(
        type = CustomFieldType.PHONE,
        name = "Phone",
        description = "Phone number input",
        icon = Icons.Default.Phone,
        iconTint = Color(0xFF6366F1) // Indigo
    ),
    FieldTypeOption(
        type = CustomFieldType.EMAIL,
        name = "Email",
        description = "Email with validation",
        icon = Icons.Default.Email,
        iconTint = Color(0xFFEF4444) // Red
    ),
    FieldTypeOption(
        type = CustomFieldType.URL,
        name = "URL",
        description = "Website link input",
        icon = Icons.Default.Link,
        iconTint = Color(0xFF14B8A6) // Teal
    ),
    FieldTypeOption(
        type = CustomFieldType.TEXTAREA,
        name = "Text Area",
        description = "Multi-line text input",
        icon = Icons.Default.Notes,
        iconTint = Color(0xFF64748B) // Slate
    ),
    FieldTypeOption(
        type = CustomFieldType.CURRENCY,
        name = "Currency",
        description = "Money with formatting",
        icon = Icons.Default.CurrencyRupee,
        iconTint = Color(0xFFF97316) // Orange
    )
)

/**
 * Main FieldTypeSelector composable
 * Displays a grid of field type options with icons, names, and descriptions
 * Highlights the selected type
 * Requirements: 2.1-2.12
 */
@Composable
fun FieldTypeSelector(
    selectedType: CustomFieldType,
    onTypeSelected: (CustomFieldType) -> Unit,
    modifier: Modifier = Modifier
) {
    val fieldTypeOptions = remember { getFieldTypeOptions() }
    
    Column(modifier = modifier) {
        Text(
            text = "Select Field Type",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFEC4899),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 400.dp)
        ) {
            items(fieldTypeOptions) { option ->
                FieldTypeCard(
                    option = option,
                    isSelected = option.type == selectedType,
                    onClick = { onTypeSelected(option.type) }
                )
            }
        }
    }
}

/**
 * Individual field type card composable
 * Shows icon, name, description and highlights when selected
 */
@Composable
private fun FieldTypeCard(
    option: FieldTypeOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        option.iconTint.copy(alpha = 0.15f)
    } else {
        Color(0xFF252542)
    }
    
    val borderColor = if (isSelected) {
        option.iconTint
    } else {
        Color(0xFF64748B).copy(alpha = 0.3f)
    }
    
    val borderWidth = if (isSelected) 2.dp else 1.dp
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon with background circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(option.iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = option.name,
                    tint = option.iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Field type name
            Text(
                text = option.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) option.iconTint else Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            // Description
            Text(
                text = option.description,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 10.sp,
                lineHeight = 12.sp
            )
        }
    }
}

/**
 * Compact version of FieldTypeSelector for smaller spaces
 * Displays as a horizontal scrollable list
 */
@Composable
fun FieldTypeSelectorCompact(
    selectedType: CustomFieldType,
    onTypeSelected: (CustomFieldType) -> Unit,
    modifier: Modifier = Modifier
) {
    val fieldTypeOptions = remember { getFieldTypeOptions() }
    
    Column(modifier = modifier) {
        Text(
            text = "Field Type",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            items(fieldTypeOptions) { option ->
                FieldTypeChip(
                    option = option,
                    isSelected = option.type == selectedType,
                    onClick = { onTypeSelected(option.type) }
                )
            }
        }
    }
}

/**
 * Compact chip version of field type option
 */
@Composable
private fun FieldTypeChip(
    option: FieldTypeOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        option.iconTint.copy(alpha = 0.15f)
    } else {
        Color(0xFF252542)
    }
    
    val borderColor = if (isSelected) option.iconTint else Color(0xFF64748B).copy(alpha = 0.3f)
    
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = option.name,
                tint = if (isSelected) option.iconTint else Color(0xFF64748B),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = option.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) option.iconTint else Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                fontSize = 9.sp
            )
        }
    }
}

/**
 * Helper function to get icon for a specific field type
 */
fun getIconForFieldType(type: CustomFieldType): ImageVector {
    return getFieldTypeOptions().find { it.type == type }?.icon ?: Icons.Default.TextFields
}

/**
 * Helper function to get icon tint color for a specific field type
 */
fun getIconTintForFieldType(type: CustomFieldType): Color {
    return getFieldTypeOptions().find { it.type == type }?.iconTint ?: Color(0xFF3B82F6)
}

/**
 * Helper function to get display name for a specific field type
 */
fun getDisplayNameForFieldType(type: CustomFieldType): String {
    return getFieldTypeOptions().find { it.type == type }?.name ?: type.name
}
