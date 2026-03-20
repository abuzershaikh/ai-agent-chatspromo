package com.message.bulksend.userdetails

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.message.bulksend.MainActivity
import com.message.bulksend.info.CountryInfo
import com.message.bulksend.info.SimCountryDetector
import com.message.bulksend.referral.InstallReferrerHelper
import com.message.bulksend.referral.ReferralManager
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch

class UserDetailsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                UserDetailsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val referralManager = remember { ReferralManager(context) }
    
    // Form state
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var referralCode by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf<CountryInfo?>(null) }
    var showCountryDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    // Country detection
    val simDetector = remember { SimCountryDetector(context) }
    
    // Install referrer helper to get pending referral code
    val installReferrerHelper = remember { InstallReferrerHelper(context) }
    val installTrackingId = remember { installReferrerHelper.getOrCreateInstallTrackingId() }
    
    // Auto-detect country and get user email on first load
    LaunchedEffect(Unit) {
        val currentCountry = simDetector.getCurrentSimCountry()
        if (currentCountry != null) {
            selectedCountry = currentCountry
        } else {
            // Default to India if no SIM detected
            selectedCountry = simDetector.getCountryByIso("IN")
        }
        
        // Get user email from Firebase Auth
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            email = currentUser.email ?: ""
            fullName = currentUser.displayName ?: ""
        }
        
        // Check for pending referral code from Play Store install
        val pendingCode = installReferrerHelper.getPendingReferralCode()
        var affiliateInstallSource: String? = null
        android.util.Log.d("UserDetails", "=== REFERRAL AUTO-FILL DEBUG ===")
        android.util.Log.d("UserDetails", "Pending referral code from InstallReferrer: $pendingCode")
        
        if (!pendingCode.isNullOrBlank() && referralCode.isBlank()) {
            referralCode = pendingCode
            affiliateInstallSource = "play_store_install"
            android.util.Log.d("UserDetails", "✅ Auto-filled referral code from install: $pendingCode")
        }
        
        // Also check intent for deep link referral (for testing without Play Store)
        val activity = context as? ComponentActivity
        val intentReferral = activity?.intent?.getStringExtra("referral_code")
        android.util.Log.d("UserDetails", "Intent referral code: $intentReferral")
        android.util.Log.d("UserDetails", "Activity intent extras: ${activity?.intent?.extras}")
        
        if (!intentReferral.isNullOrBlank() && referralCode.isBlank()) {
            referralCode = intentReferral.uppercase()
            affiliateInstallSource = "deep_link"
            android.util.Log.d("UserDetails", "✅ Auto-filled referral code from intent: $intentReferral")
        }
        
        android.util.Log.d("UserDetails", "Final referral code value: $referralCode")

        if (!referralCode.isNullOrBlank() && affiliateInstallSource != null) {
            try {
                val installResult = referralManager.trackAffiliateInstall(
                    referralCode = referralCode,
                    source = affiliateInstallSource,
                    installId = installTrackingId
                )
                android.util.Log.d(
                    "UserDetails",
                    "Affiliate install tracking: success=${installResult.success}, message=${installResult.message}"
                )
            } catch (e: Exception) {
                android.util.Log.e("UserDetails", "Error tracking affiliate install: ${e.message}", e)
            }
        }
    }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF8F9FA),
            Color(0xFFE9ECEF),
            Color(0xFFDEE2E6)
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // Header
            Text(
                "Welcome to ChatsPromo!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C3E50),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Please fill in your details to get started",
                fontSize = 16.sp,
                color = Color(0xFF6C757D),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Full Name Field
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF667eea),
                            focusedLabelColor = Color(0xFF667eea),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        )
                    )
                    
                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF667eea),
                            focusedLabelColor = Color(0xFF667eea),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        )
                    )
                    
                    // Phone Number Field with Country Code
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Phone Number") },
                        leadingIcon = {
                            // Country Code Selector as leading icon
                            Row(
                                modifier = Modifier
                                    .clickable { showCountryDialog = true }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    selectedCountry?.iso ?: "IN",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                Text(
                                    "+${selectedCountry?.countryCode ?: "91"}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF667eea),
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color(0xFF6C757D),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        placeholder = { Text("Enter phone number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF667eea),
                            focusedLabelColor = Color(0xFF667eea),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        )
                    )
                    
                    // Business Name Field
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = { Text("Business Name") },
                        leadingIcon = {
                            Icon(Icons.Default.Business, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF667eea),
                            focusedLabelColor = Color(0xFF667eea),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        )
                    )
                    
                    // Affiliate Code Field (Optional)
                    OutlinedTextField(
                        value = referralCode,
                        onValueChange = { referralCode = it },
                        label = { Text("Affiliate Code (Optional)") },
                        leadingIcon = {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null)
                        },
                        placeholder = { Text("Paste creator or affiliate code if you have one") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF667eea),
                            focusedLabelColor = Color(0xFF667eea),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        )
                    )

                    Text(
                        text = "Installed from a creator, YouTube or social media link? Your affiliate code is auto-detected from Play Store when available.",
                        fontSize = 12.sp,
                        color = Color(0xFF6C757D)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Submit Button
                    Button(
                        onClick = {
                            if (fullName.isBlank() || email.isBlank() || phoneNumber.isBlank() || businessName.isBlank()) {
                                Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            isLoading = true
                            scope.launch {
                                try {
                                    val currentUser = FirebaseAuth.getInstance().currentUser
                                    val userId = currentUser?.uid ?: ""
                                    val fullPhoneNumber = "+${selectedCountry?.countryCode ?: "91"}$phoneNumber"
                                    val countryCodeValue = selectedCountry?.countryCode ?: "91"
                                    val countryIsoValue = selectedCountry?.iso ?: "IN"
                                    val countryValue = selectedCountry?.country ?: "India"
                                    
                                    // Log referral code for debugging
                                    android.util.Log.d("UserDetails", "=== REFERRAL CODE DEBUG ===")
                                    android.util.Log.d("UserDetails", "Entered referral code: '$referralCode'")
                                    
                                    // Properly handle referral code - trim and uppercase
                                    val finalReferralCode = if (referralCode.trim().isNotBlank()) {
                                        referralCode.trim().uppercase()
                                    } else {
                                        null
                                    }
                                    android.util.Log.d("UserDetails", "Final referral code to save: $finalReferralCode")
                                    
                                    val userDetails = hashMapOf<String, Any?>(
                                        "userId" to userId,
                                        "fullName" to fullName,
                                        "email" to email,
                                        "phoneNumber" to fullPhoneNumber,
                                        "businessName" to businessName,
                                        "countryCode" to countryCodeValue,
                                        "countryIso" to countryIsoValue,
                                        "country" to countryValue,
                                        "referredBy" to finalReferralCode,
                                        "timestamp" to System.currentTimeMillis()
                                    )
                                    
                                    android.util.Log.d("UserDetails", "UserDetails map: $userDetails")
                                    
                                    // Add userStatus = "registered" to the data
                                    userDetails["userStatus"] = "registered"
                                    
                                    // Save to Firestore
                                    FirebaseFirestore.getInstance()
                                        .collection("userDetails")
                                        .document(userId)
                                        .set(userDetails, SetOptions.merge())
                                        .addOnSuccessListener {
                                            android.util.Log.d("UserDetails", "✅ Saved to Firestore successfully with status: registered")
                                            
                                            // Save to SharedPreferences
                                            val userDetailsPrefs = UserDetailsPreferences(context)
                                            userDetailsPrefs.saveUserDetails(
                                                userId = userId,
                                                fullName = fullName,
                                                email = email,
                                                phoneNumber = fullPhoneNumber,
                                                businessName = businessName,
                                                countryCode = countryCodeValue,
                                                countryIso = countryIsoValue,
                                                country = countryValue,
                                                referralCode = finalReferralCode
                                            )
                                            android.util.Log.d("UserDetails", "✅ Saved to UserDetailsPreferences with referralCode: $finalReferralCode")
                                            
                                            // Continue affiliate setup in background even after activity finishes
                                            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                try {
                                                    val generateResult = referralManager.generateReferralCode(fullName)
                                                    if (generateResult.success) {
                                                        android.util.Log.d("UserDetails", "✅ Referral code generated: ${generateResult.referralCode}")
                                                    } else {
                                                        android.util.Log.e("UserDetails", "❌ Failed to generate referral code: ${generateResult.message}")
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.e("UserDetails", "❌ Error generating referral code: ${e.message}")
                                                }
                                                
                                                // 2. Process referral code if user entered one
                                                if (!finalReferralCode.isNullOrBlank()) {
                                                    try {
                                                        val processResult = referralManager.processReferralCode(
                                                            referralCode = finalReferralCode,
                                                            installId = installTrackingId
                                                        )
                                                        if (processResult.success) {
                                                            android.util.Log.d("UserDetails", "✅ Referral code processed: ${processResult.referrerName}")
                                                            // Clear pending referral code after successful processing
                                                            installReferrerHelper.clearPendingReferralCode()
                                                        } else {
                                                            android.util.Log.e("UserDetails", "❌ Failed to process referral: ${processResult.message}")
                                                        }
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("UserDetails", "❌ Error processing referral code: ${e.message}")
                                                    }
                                                }
                                            }
                                            
                                            Toast.makeText(context, "Details saved successfully!", Toast.LENGTH_SHORT).show()
                                            context.startActivity(Intent(context, MainActivity::class.java))
                                            (context as ComponentActivity).finish()
                                        }
                                        .addOnFailureListener { e ->
                                            android.util.Log.e("UserDetails", "❌ Firestore save failed", e)
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            isLoading = false
                                        }
                                } catch (e: Exception) {
                                    android.util.Log.e("UserDetails", "❌ Exception in submit", e)
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF667eea)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                "Submit",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Country Selection Dialog
    if (showCountryDialog) {
        CountrySelectionDialog(
            simDetector = simDetector,
            onCountrySelected = { country ->
                selectedCountry = country
                showCountryDialog = false
            },
            onDismiss = { showCountryDialog = false }
        )
    }
}

@Composable
fun CountrySelectionDialog(
    simDetector: SimCountryDetector,
    onCountrySelected: (CountryInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val allCountries = remember { simDetector.getAllMccData().distinctBy { it.iso }.sortedBy { it.country } }
    
    val filteredCountries = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            allCountries
        } else {
            allCountries.filter { 
                it.country.contains(searchQuery, ignoreCase = true) ||
                it.iso.contains(searchQuery, ignoreCase = true) ||
                it.countryCode.contains(searchQuery)
            }
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Select Country",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search country...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Countries List
                LazyColumn {
                    items(filteredCountries) { country ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { onCountrySelected(country) },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        country.country,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.Black
                                    )
                                    Text(
                                        country.iso,
                                        fontSize = 12.sp,
                                        color = Color(0xFF6C757D)
                                    )
                                }
                                Text(
                                    "+${country.countryCode}",
                                    fontSize = 14.sp,
                                    color = Color(0xFF667eea),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
