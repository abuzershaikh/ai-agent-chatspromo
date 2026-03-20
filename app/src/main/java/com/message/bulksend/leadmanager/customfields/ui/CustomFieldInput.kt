package com.message.bulksend.leadmanager.customfields.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.customfields.model.CustomField
import com.message.bulksend.leadmanager.database.entities.CustomFieldType
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main reusable composable for rendering custom field inputs
 * Renders appropriate input based on field type
 * Requirements: 3.2, 3.3, 3.4, 3.5, 3.6, 3.7
 */
@Composable
fun CustomFieldInput(
    field: CustomField,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    Column(modifier = modifier) {
        when (field.fieldType) {
            CustomFieldType.TEXT -> TextFieldInput(field, value, onValueChange, isError)
            CustomFieldType.NUMBER -> NumberFieldInput(field, value, onValueChange, isError)
            CustomFieldType.DATE -> DateFieldInput(field, value, onValueChange, isError)
            CustomFieldType.TIME -> TimeFieldInput(field, value, onValueChange, isError)
            CustomFieldType.DATETIME -> DateTimeFieldInput(field, value, onValueChange, isError)
            CustomFieldType.DROPDOWN -> DropdownFieldInput(field, value, onValueChange, isError)
            CustomFieldType.CHECKBOX -> CheckboxFieldInput(field, value, onValueChange)
            CustomFieldType.PHONE -> PhoneFieldInput(field, value, onValueChange, isError)
            CustomFieldType.EMAIL -> EmailFieldInput(field, value, onValueChange, isError)
            CustomFieldType.URL -> UrlFieldInput(field, value, onValueChange, isError)
            CustomFieldType.TEXTAREA -> TextAreaFieldInput(field, value, onValueChange, isError)
            CustomFieldType.CURRENCY -> CurrencyFieldInput(field, value, onValueChange, isError)
        }

        // Show error message if present
        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Text field input for TEXT type
 * Requirements: 3.2
 */
@Composable
private fun TextFieldInput(
    field: CustomField,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { FieldLabel(field) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
        colors = customFieldInputColors()
    )
}

/**
 * Number field input for NUMBER type with number keyboard
 * Requirements: 3.3
 */
@Composable
private fun NumberFieldInput(
    field: CustomField,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            // Only allow numeric input
            if (newValue.isEmpty() || newValue.matches(Regex("^-?\\d*\\.?\\d*$"))) {
                onValueChange(newValue)
            }
        },
        label = { FieldLabel(field) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = customFieldInputColors()
    )
}

/**
 * Date field input for DATE type with DatePickerDialog
 * Requirements: 3.4
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFieldInput(
    field: CustomField,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    
    // Parse existing value if present
    LaunchedEffect(value) {
        if (value.isNotEmpty()) {
            try {
                dateFormat.parse(value)?.let { calendar.time = it }
            } catch (e: Exception) { /* Keep current date */ }
        }
    }
    
    OutlinedTextField(
        value = value,
        onValueChange = { },
        label = { FieldLabel(field) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        calendar.set(year, month, dayOfMonth)
                        onValueChange(dateFormat.format(calendar.time))
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
        readOnly = true,
        isError = isError,
        trailingIcon = {
            IconButton(onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        calendar.set(year, month, dayOfMonth)
                        onValueChange(dateFormat.format(calendar.time))
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }) {
                Icon(Icons.Default.DateRange, "Select date", tint = Color(0xFF3B82F6))
            }
        },
        colors = customFieldInputColors()
    )
}

/**
 * Time field input for TIME type with TimePickerDialog
 * Requirements: 3.5
 */
@Composable
private fun TimeFieldInput(
    field: CustomField,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    
    // Parse existing value if present
    LaunchedEffect(value) {
        if (value.isNotEmpty()) {
            try {
                timeFormat.parse(value)?.let { calendar.time = it }
            } catch (e: Exception) { /* Keep current time */ }
        }
    }
    
    OutlinedTextField(
        value = value,
        onValueChange = { },
        label = { FieldLabel(field) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        onValueChange(timeFormat.format(calendar.time))
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
        readOnly = true,
        isError = isError,
        trailingIcon = {
            IconButton(onClick = {
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        onValueChange(timeFormat.format(calendar.time))
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            }) {
                Icon(Icons.Default.Schedule, "Select time", tint = Color(0xFF3B82F6))
            }
        },
        colors = customFieldInputColors()
    )
}


/**
 * DateTime field input for DATETIME type with both date and time pickers
 * Requirements: 3.4, 3.5
 */
@Composable
private fun DateTimeFieldInput(
    field: CustomField,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    val dateTimeFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    
    // Parse existing value if present
    LaunchedEffect(value) {
        if (value.isNotEmpty()) {
            try {
                dateTimeFormat.parse(value)?.let { calendar.time = it }
            } catch (e: Exception) { /* Keep current datetime */ }
        }
    }
    
    fun showDateTimePicker() {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                // After date selection, show time picker
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        onValueChange(dateTimeFormat.format(calendar.time))
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    
    OutlinedTextField(
        value = value,
        onValueChange = { },
        label = { FieldLabel(field) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDateTimePicker() },
        readOnly = true,
        isError = isError,
        trailingIcon = {
            IconButton(onClick = { showDateTimePicker() }) {
                Icon(Icons.Default.Event, "Select date and time", tint = Color(0xFF3B82F6))
            }
        },
        colors = customFieldInputColors()
    )
}

/**
 * Dropdown field input for DROPDOWN type with ExposedDropdownMenu
 * Requirements: 3.6
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownFieldInput(
    field: CustomField,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { },
            label = { FieldLabel(field) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            readOnly = true,
            isError = isError,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = customFieldInputColors()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            field.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

/**
 * Checkbox field input for CHECKBOX type with Switch
 * Requirements: 3.7
 */
@Composable
private fun CheckboxFieldInput(
    field: CustomField,
    value: String,
    onValueChange: (String) -> Unit
) {
    val isChecked = value.equals("true", ignoreCase = true)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = field.fieldName,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            if (field.isRequired) {
                Text(
                    text = " *",
                    color = Color(0xFFEF4444),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = { onValueChange(it.toString()) },
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFF10B981),
                checkedThumbColor = Color.White
            )
        )
    }
}

/**
 * Phone field input for PHONE type with phone keyboard
 * Requirements: 3.2 (phone variant)
 */
@Composable
private fun PhoneFieldInput(
    field: CustomField,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            // Allow only phone number characters
            if (newValue.isEmpty() || newValue.matches(Regex("^[+\\d\\s()-]*$"))) {
                onValueChange(newValue)
            }
        },
        label = { FieldLabel(field) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        leadingIcon = {
            Icon(Icons.Default.Phone, "Phone", tint = Color(0xFF64748B))
        },
        colors = customFieldInputColors()
    )
}


/**
 * Email field input for EMAIL type with email keyboard and validation
 * Requirements: 3.2 (email variant)
 */
@Composable
private fun EmailFieldInput(
    field: CustomField,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    val isValidEmail = remember(value) {
        value.isEmpty() || android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()
    }
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { FieldLabel(field) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError || !isValidEmail,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        leadingIcon = {
            Icon(Icons.Default.Email, "Email", tint = Color(0xFF64748B))
        },
        supportingText = if (!isValidEmail && value.isNotEmpty()) {
            { Text("Invalid email format", color = Color(0xFFEF4444)) }
        } else null,
        colors = customFieldInputColors()
    )
}

/**
 * URL field input for URL type
 * Requirements: 3.2 (URL variant)
 */
@Composable
private fun UrlFieldInput(
    field: CustomField,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    val isValidUrl = remember(value) {
        value.isEmpty() || android.util.Patterns.WEB_URL.matcher(value).matches()
    }
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { FieldLabel(field) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError || !isValidUrl,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        leadingIcon = {
            Icon(Icons.Default.Link, "URL", tint = Color(0xFF64748B))
        },
        supportingText = if (!isValidUrl && value.isNotEmpty()) {
            { Text("Invalid URL format", color = Color(0xFFEF4444)) }
        } else null,
        colors = customFieldInputColors()
    )
}

/**
 * TextArea field input for TEXTAREA type with multiline support
 * Requirements: 3.2 (textarea variant)
 */
@Composable
private fun TextAreaFieldInput(
    field: CustomField,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { FieldLabel(field) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp),
        singleLine = false,
        maxLines = 5,
        minLines = 3,
        isError = isError,
        colors = customFieldInputColors()
    )
}

/**
 * Currency field input for CURRENCY type with formatting
 * Requirements: 3.3 (currency variant)
 */
@Composable
private fun CurrencyFieldInput(
    field: CustomField,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    val currencyFormat = remember { DecimalFormat("#,##0.00") }
    
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            // Allow only currency-valid input
            val cleaned = newValue.replace(",", "")
            if (cleaned.isEmpty() || cleaned.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                onValueChange(cleaned)
            }
        },
        label = { FieldLabel(field) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        leadingIcon = {
            Text(
                text = "₹",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(start = 12.dp)
            )
        },
        colors = customFieldInputColors()
    )
}

/**
 * Helper composable for field label with required indicator
 */
@Composable
private fun FieldLabel(field: CustomField) {
    Row {
        Text(field.fieldName, color = Color(0xFF94A3B8))
        if (field.isRequired) {
            Text(" *", color = Color(0xFFEF4444))
        }
    }
}

/**
 * Common colors for custom field inputs - white text on dark background
 */
@Composable
fun customFieldInputColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF3B82F6),
    unfocusedBorderColor = Color(0xFF64748B),
    errorBorderColor = Color(0xFFEF4444),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    errorTextColor = Color.White,
    focusedLabelColor = Color(0xFF3B82F6),
    unfocusedLabelColor = Color(0xFF94A3B8),
    errorLabelColor = Color(0xFFEF4444),
    cursorColor = Color(0xFF3B82F6),
    focusedPlaceholderColor = Color(0xFF64748B),
    unfocusedPlaceholderColor = Color(0xFF64748B)
)

/**
 * Helper function to validate email format
 */
fun isValidEmail(email: String): Boolean {
    return email.isEmpty() || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
}

/**
 * Helper function to validate URL format
 */
fun isValidUrl(url: String): Boolean {
    return url.isEmpty() || android.util.Patterns.WEB_URL.matcher(url).matches()
}
