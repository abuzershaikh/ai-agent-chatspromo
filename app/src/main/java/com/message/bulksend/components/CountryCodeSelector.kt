package com.message.bulksend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.message.bulksend.utils.Country
import com.message.bulksend.utils.CountryCodeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryCodeSelector(
    selectedCountry: Country?,
    onCountrySelected: (Country) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val countries = remember { CountryCodeManager.loadCountries(context) }
    
    val filteredCountries = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            countries
        } else {
            countries.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.dial_code.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    // Country Code Field
    OutlinedTextField(
        value = selectedCountry?.dial_code ?: "",
        onValueChange = { },
        label = { Text("Country Code") },
        placeholder = { Text("Select country") },
        leadingIcon = {
            if (selectedCountry != null) {
                Text(
                    selectedCountry.flag,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            } else {
                Icon(Icons.Default.Phone, contentDescription = "Country Code")
            }
        },
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select")
            }
        },
        readOnly = true,
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
    
    // Country Selection Dialog
    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Select Country",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        IconButton(onClick = { showDialog = false }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        placeholder = { Text("Search country or code...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    
                    // Countries List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(filteredCountries) { country ->
                            CountryItem(
                                country = country,
                                isSelected = selectedCountry?.dial_code == country.dial_code,
                                onClick = {
                                    onCountrySelected(country)
                                    // Mark as user-selected so it won't be overwritten by auto-detect
                                    CountryCodeManager.saveSelectedCountry(context, country, userSelected = true)
                                    showDialog = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CountryItem(
    country: Country,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                country.flag,
                fontSize = 28.sp,
                modifier = Modifier.padding(end = 12.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    country.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    country.dial_code,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
