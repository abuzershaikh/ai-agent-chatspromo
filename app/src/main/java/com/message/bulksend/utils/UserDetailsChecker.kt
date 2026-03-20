package com.message.bulksend.utils

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.message.bulksend.userdetails.UserDetailsPreferences
import kotlinx.coroutines.tasks.await

object UserDetailsChecker {
    
    /**
     * Check if current user has filled their details
     * First checks SharedPreferences for faster access, then Firestore as fallback
     * Checks for required fields: fullName, email, phoneNumber, businessName
     */
    suspend fun hasUserDetails(context: Context? = null): Boolean {
        return try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                false
            } else {
                // First check SharedPreferences if context is available
                if (context != null) {
                    val userDetailsPrefs = UserDetailsPreferences(context)
                    if (userDetailsPrefs.isDetailsSaved() && 
                        userDetailsPrefs.getUserId() == currentUser.uid) {
                        // Also verify required fields are present
                        val fullName = userDetailsPrefs.getFullName()
                        val email = userDetailsPrefs.getEmail()
                        if (!fullName.isNullOrBlank() && !email.isNullOrBlank()) {
                            return true
                        }
                    }
                }
                
                // Fallback to Firestore check
                val document = FirebaseFirestore.getInstance()
                    .collection("userDetails")
                    .document(currentUser.uid)
                    .get()
                    .await()
                
                if (document.exists()) {
                    // Check if required fields are present
                    val fullName = document.getString("fullName")
                    val email = document.getString("email")
                    val phoneNumber = document.getString("phoneNumber")
                    val businessName = document.getString("businessName")
                    
                    // User has filled details only if all required fields are present
                    !fullName.isNullOrBlank() && 
                    !email.isNullOrBlank() && 
                    !phoneNumber.isNullOrBlank() && 
                    !businessName.isNullOrBlank()
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get user details from SharedPreferences first, then Firestore as fallback
     */
    suspend fun getUserDetails(context: Context? = null): Map<String, Any?>? {
        return try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                null
            } else {
                // First try SharedPreferences if context is available
                if (context != null) {
                    val userDetailsPrefs = UserDetailsPreferences(context)
                    if (userDetailsPrefs.isDetailsSaved() && 
                        userDetailsPrefs.getUserId() == currentUser.uid) {
                        return userDetailsPrefs.getAllUserDetails()
                    }
                }
                
                // Fallback to Firestore
                val document = FirebaseFirestore.getInstance()
                    .collection("userDetails")
                    .document(currentUser.uid)
                    .get()
                    .await()
                
                if (document.exists()) {
                    document.data
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Sync user details from Firestore to SharedPreferences
     */
    suspend fun syncUserDetailsToPreferences(context: Context): Boolean {
        return try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                false
            } else {
                val document = FirebaseFirestore.getInstance()
                    .collection("userDetails")
                    .document(currentUser.uid)
                    .get()
                    .await()
                
                if (document.exists()) {
                    val data = document.data
                    if (data != null) {
                        val userDetailsPrefs = UserDetailsPreferences(context)
                        userDetailsPrefs.saveUserDetails(
                            userId = data["userId"] as? String ?: currentUser.uid,
                            fullName = data["fullName"] as? String ?: "",
                            email = data["email"] as? String ?: "",
                            phoneNumber = data["phoneNumber"] as? String ?: "",
                            businessName = data["businessName"] as? String ?: "",
                            countryCode = data["countryCode"] as? String ?: "91",
                            countryIso = data["countryIso"] as? String ?: "IN",
                            country = data["country"] as? String ?: "India",
                            referralCode = data["referralCode"] as? String
                        )
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}