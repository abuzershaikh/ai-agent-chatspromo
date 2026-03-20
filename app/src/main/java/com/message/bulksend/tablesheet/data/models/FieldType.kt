package com.message.bulksend.tablesheet.data.models

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Complete Field Types for TableSheet
 * Based on Table Notes documentation
 */
object FieldType {
    const val TEXT = "STRING"
    const val NUMBER = "INTEGER"
    const val AMOUNT = "AMOUNT"
    const val DATE = "DATEONLY"
    const val TIME = "TIME"
    const val CHECKBOX = "CHECKBOX"
    const val SELECT = "SELECT"
    const val PHONE = "PHONE"
    const val EMAIL = "EMAIL"
    const val IMAGE = "IMAGE"
    const val LINK = "LINK"
    const val DRAW = "DRAW"
    const val AUDIO = "AUDIO"
    const val MAP = "MAP"
    const val FORMULA = "FORMULA"
    const val URL = "URL"
    const val MULTILINE = "MULTILINE"
    const val PRIORITY = "PRIORITY"
}

/**
 * Field Type Configuration with icon, color, and display info
 */
data class FieldTypeConfig(
    val type: String,
    val name: String,
    val icon: ImageVector,
    val color: Color,
    val description: String,
    val hasOptions: Boolean = false,  // For SELECT type
    val hasFormat: Boolean = false,   // For NUMBER/AMOUNT
    val hasDateFormat: Boolean = false // For DATE/TIME
)

/**
 * All available field types with their configurations
 */
object FieldTypes {
    
    val allTypes: List<FieldTypeConfig> = listOf(
        FieldTypeConfig(
            type = FieldType.TEXT,
            name = "Text",
            icon = Icons.Default.TextFields,
            color = Color(0xFF6366F1),
            description = "Plain text input"
        ),
        FieldTypeConfig(
            type = FieldType.NUMBER,
            name = "Number",
            icon = Icons.Default.Numbers,
            color = Color(0xFF10B981),
            description = "Numeric values",
            hasFormat = true
        ),
        FieldTypeConfig(
            type = FieldType.AMOUNT,
            name = "Amount",
            icon = Icons.Default.AttachMoney,
            color = Color(0xFFF59E0B),
            description = "Currency with formatting",
            hasFormat = true
        ),
        FieldTypeConfig(
            type = FieldType.DATE,
            name = "Date",
            icon = Icons.Default.CalendarMonth,
            color = Color(0xFFEC4899),
            description = "Date picker",
            hasDateFormat = true
        ),
        FieldTypeConfig(
            type = FieldType.TIME,
            name = "Time",
            icon = Icons.Default.Schedule,
            color = Color(0xFF8B5CF6),
            description = "Time picker",
            hasDateFormat = true
        ),
        FieldTypeConfig(
            type = FieldType.CHECKBOX,
            name = "Checkbox",
            icon = Icons.Default.CheckBox,
            color = Color(0xFF3B82F6),
            description = "True/False toggle"
        ),
        FieldTypeConfig(
            type = FieldType.SELECT,
            name = "Dropdown",
            icon = Icons.Default.ArrowDropDownCircle,
            color = Color(0xFF14B8A6),
            description = "Select from options",
            hasOptions = true
        ),
        FieldTypeConfig(
            type = FieldType.PHONE,
            name = "Phone",
            icon = Icons.Default.Phone,
            color = Color(0xFF06B6D4),
            description = "Phone number with call"
        ),
        FieldTypeConfig(
            type = FieldType.EMAIL,
            name = "Email",
            icon = Icons.Default.Email,
            color = Color(0xFFEF4444),
            description = "Email address"
        ),
        FieldTypeConfig(
            type = FieldType.IMAGE,
            name = "Image",
            icon = Icons.Default.Image,
            color = Color(0xFFF97316),
            description = "Photo attachment"
        ),
        FieldTypeConfig(
            type = FieldType.URL,
            name = "Link/URL",
            icon = Icons.Default.Link,
            color = Color(0xFF0EA5E9),
            description = "Web link"
        ),
        FieldTypeConfig(
            type = FieldType.MAP,
            name = "Location",
            icon = Icons.Default.LocationOn,
            color = Color(0xFFEF4444),
            description = "GPS coordinates"
        ),
        FieldTypeConfig(
            type = FieldType.MULTILINE,
            name = "Long Text",
            icon = Icons.Default.Notes,
            color = Color(0xFF7C3AED),
            description = "Multi-line text"
        ),
        FieldTypeConfig(
            type = FieldType.DRAW,
            name = "Signature",
            icon = Icons.Default.Draw,
            color = Color(0xFFD946EF),
            description = "Drawing/Signature"
        ),
        FieldTypeConfig(
            type = FieldType.AUDIO,
            name = "Audio",
            icon = Icons.Default.Mic,
            color = Color(0xFFF43F5E),
            description = "Voice recording"
        ),
        FieldTypeConfig(
            type = FieldType.PRIORITY,
            name = "Priority",
            icon = Icons.Default.Flag,
            color = Color(0xFF9C27B0),
            description = "Priority with colors",
            hasOptions = true
        )
    )
    
    fun getConfig(type: String): FieldTypeConfig {
        return allTypes.find { it.type == type } ?: allTypes.first()
    }
    
    fun getIcon(type: String): ImageVector {
        return getConfig(type).icon
    }
    
    fun getColor(type: String): Color {
        return getConfig(type).color
    }
    
    fun getName(type: String): String {
        return getConfig(type).name
    }
}

/**
 * Column Style Configuration
 */
data class ColumnStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val alignment: Int = 0,  // 0=left, 1=center, 2=right
    val bgColor: String? = null,
    val textColor: String? = null
)

/**
 * Number Format Configuration (for NUMBER/AMOUNT types)
 */
data class NumberFormat(
    val decimals: Int = 0,
    val prefix: String = "",      // Currency symbol like ₹, $
    val suffix: String = "",
    val separator: Boolean = true, // Thousand separator
    val sign: Int = 0             // 0=normal, 1=always show +
)

/**
 * Select/Dropdown Options
 */
data class SelectOptions(
    val options: List<String> = emptyList(),
    val allowMultiple: Boolean = false,
    val defaultValue: String? = null
)
