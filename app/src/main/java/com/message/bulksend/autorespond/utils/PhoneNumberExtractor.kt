package com.message.bulksend.autorespond.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Utility to extract phone number from contact name
 */
object PhoneNumberExtractor {
    
    private const val TAG = "PhoneNumberExtractor"
    
    // Global phone number regex patterns
    // Matches: +91 1234567890, +1-234-567-8900, 1234567890, etc.
    private val PHONE_REGEX = Regex("^\\+?[1-9]\\d{1,14}$") // E.164 format
    private val PHONE_WITH_SPACES = Regex("^\\+?[0-9\\s\\-()]{10,}$") // With spaces/dashes
    
    // Valid country dial codes from country_dial_info.json
    private val VALID_DIAL_CODES = setOf(
        "+93", "+358", "+355", "+213", "+1684", "+376", "+244", "+1264", "+672", "+1268",
        "+54", "+374", "+297", "+61", "+43", "+994", "+1242", "+973", "+880", "+1246",
        "+375", "+32", "+501", "+229", "+1441", "+975", "+591", "+387", "+267", "+47",
        "+55", "+246", "+673", "+359", "+226", "+257", "+855", "+237", "+1", "+238",
        "+345", "+236", "+235", "+56", "+86", "+57", "+269", "+242", "+243", "+682",
        "+506", "+225", "+385", "+53", "+357", "+420", "+45", "+253", "+1767", "+1849",
        "+593", "+20", "+503", "+240", "+291", "+372", "+251", "+500", "+298", "+679",
        "+33", "+594", "+689", "+262", "+241", "+220", "+995", "+49", "+233", "+350",
        "+30", "+299", "+1473", "+590", "+1671", "+502", "+44", "+224", "+245", "+592",
        "+509", "+379", "+504", "+852", "+36", "+354", "+91", "+62", "+98", "+964",
        "+353", "+972", "+39", "+1876", "+81", "+962", "+7", "+254", "+686", "+850",
        "+82", "+383", "+965", "+996", "+856", "+371", "+961", "+266", "+231", "+218",
        "+423", "+370", "+352", "+853", "+389", "+261", "+265", "+60", "+960", "+223",
        "+356", "+692", "+596", "+222", "+230", "+52", "+691", "+373", "+377", "+976",
        "+382", "+1664", "+212", "+258", "+95", "+264", "+674", "+977", "+31", "+599",
        "+687", "+64", "+505", "+227", "+234", "+683", "+1670", "+968", "+92", "+680",
        "+970", "+507", "+675", "+595", "+51", "+63", "+48", "+351", "+1939", "+974",
        "+40", "+250", "+1869", "+1758", "+508", "+1784", "+685", "+378", "+239", "+966",
        "+221", "+381", "+248", "+232", "+65", "+421", "+386", "+677", "+252", "+27",
        "+211", "+34", "+94", "+249", "+597", "+268", "+46", "+41", "+963", "+886",
        "+992", "+255", "+66", "+670", "+228", "+690", "+676", "+1868", "+216", "+90",
        "+993", "+1649", "+688", "+256", "+380", "+971", "+598", "+998", "+678", "+58",
        "+84", "+1284", "+1340", "+681", "+967", "+260", "+263"
    )
    
    /**
     * Check if string is a phone number
     */
    fun isPhoneNumber(text: String): Boolean {
        if (text.isEmpty()) return false
        
        // Remove common separators
        val cleaned = text.replace(Regex("[\\s\\-().]"), "")
        
        // Check if starts with + (international format)
        if (cleaned.startsWith("+")) {
            // Extract dial code (e.g., +1, +91, +1234)
            val dialCodeMatch = Regex("^(\\+\\d{1,4})").find(cleaned)
            if (dialCodeMatch != null) {
                val dialCode = dialCodeMatch.value
                // Check if it's a valid country code
                if (VALID_DIAL_CODES.contains(dialCode)) {
                    // Check if remaining part has digits
                    val remaining = cleaned.substring(dialCode.length)
                    return remaining.length >= 6 && remaining.all { it.isDigit() }
                }
            }
            // Fallback: check if it has 11+ digits after +
            return cleaned.length >= 11 && cleaned.substring(1).all { it.isDigit() }
        }
        
        // Check if contains 10+ consecutive digits (without country code)
        return cleaned.matches(Regex("^\\d{10,15}$"))
    }
    
    /**
     * Get contact name from phone number
     */
    fun getContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isEmpty() || phoneNumber == "Unknown") return null
        if (!hasContactsPermission(context)) {
            Log.w(TAG, "Contacts permission not granted - cannot lookup name for $phoneNumber")
            return null
        }
        
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            
            val cleanedInput = cleanPhoneNumber(phoneNumber)
            val inputLast10 = cleanedInput.takeLast(10)
            
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    
                    if (nameIndex >= 0 && numberIndex >= 0) {
                        val contactNumber = cleanPhoneNumber(it.getString(numberIndex))
                        val contactLast10 = contactNumber.takeLast(10)
                        
                        // Compare last 10 digits (works for all countries)
                        if (inputLast10.isNotEmpty() && contactLast10.isNotEmpty() && 
                            inputLast10 == contactLast10) {
                            val name = it.getString(nameIndex)
                            Log.d(TAG, "✓ Contact name found for $phoneNumber: $name")
                            return name
                        }
                    }
                }
            }
            
            Log.d(TAG, "✗ No contact name found for $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting contact name: ${e.message}")
        }
        
        return null
    }
    
    /**
     * Extract phone number from sender name using Contacts
     */
    fun getPhoneNumber(context: Context, senderName: String): String? {
        val name = senderName.trim()
        if (name.isEmpty()) return null
        
        // Check if sender name already contains phone number
        if (name.matches(Regex(".*\\d{10,}.*"))) {
            val phoneMatch = Regex("\\d{10,}").find(name)
            if (phoneMatch != null) {
                return phoneMatch.value
            }
        }
        
        if (!hasContactsPermission(context)) {
            Log.w(TAG, "Contacts permission not granted - cannot resolve phone for $name")
            return null
        }
        
        // Prefer WhatsApp contact data first (usually includes country code)
        try {
            val whatsappNumber = getWhatsAppNumberFromContacts(context, name)
            if (!whatsappNumber.isNullOrBlank()) {
                Log.d(TAG, "Phone number found from WhatsApp profile for $name: $whatsappNumber")
                return whatsappNumber
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting WhatsApp number: ${e.message}")
        }
        
        // Try to find in contacts (exact match first)
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
            )
            
            val exactSelection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?"
            val exactArgs = arrayOf(name)
            
            val exactCursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                exactSelection,
                exactArgs,
                null
            )
            
            exactCursor?.use {
                if (it.moveToFirst()) {
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val normalizedIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
                    val resolved = resolveBestNumber(context, it, numberIndex, normalizedIndex)
                    if (!resolved.isNullOrBlank()) {
                        Log.d(TAG, "Phone number found for exact name $name: $resolved")
                        return resolved
                    }
                }
            }
            
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$name%")
            
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val normalizedIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
                    val resolved = resolveBestNumber(context, it, numberIndex, normalizedIndex)
                    if (!resolved.isNullOrBlank()) {
                        Log.d(TAG, "Phone number found for $name: $resolved")
                        return resolved
                    }
                }
            }
            
            // Fallback: full scan with normalized name matching
            val normalizedSender = normalizeName(name)
            if (normalizedSender.isNotEmpty()) {
                var fallbackNumber: String? = null
                val allCursor: Cursor? = context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    null
                )
                
                allCursor?.use {
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val normalizedIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
                    
                    while (it.moveToNext()) {
                        val contactName = if (nameIndex >= 0) it.getString(nameIndex) else null
                        val contactNumber = resolveBestNumber(context, it, numberIndex, normalizedIndex)
                        
                        if (contactName.isNullOrBlank() || contactNumber.isNullOrBlank()) continue
                        
                        if (contactName.equals(name, ignoreCase = true)) {
                            Log.d(TAG, "Phone number found for case-insensitive name $name: $contactNumber")
                            return contactNumber
                        }
                        
                        if (namesMatch(contactName, name, normalizedSender)) {
                            if (fallbackNumber == null) {
                                fallbackNumber = contactNumber
                            }
                        }
                    }
                }
                
                if (fallbackNumber != null) {
                    Log.d(TAG, "Phone number found for normalized name $name: $fallbackNumber")
                    return fallbackNumber
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting phone number: ${e.message}")
        }
        
        return null
    }
    
    /**
     * Clean phone number - remove spaces, dashes, etc.
     */
    private fun cleanPhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace(Regex("[^0-9+]"), "")
    }

    /**
     * Resolve best phone number with country code preference
     */
    private fun resolveBestNumber(
        context: Context,
        cursor: Cursor,
        numberIndex: Int,
        normalizedIndex: Int
    ): String? {
        val normalized = if (normalizedIndex >= 0) cursor.getString(normalizedIndex) else null
        if (!normalized.isNullOrBlank()) {
            return cleanPhoneNumber(normalized)
        }
        
        val raw = if (numberIndex >= 0) cursor.getString(numberIndex) else null
        if (raw.isNullOrBlank()) return null
        
        val cleanedRaw = cleanPhoneNumber(raw)
        if (cleanedRaw.startsWith("+")) {
            return cleanedRaw
        }
        
        val countryIso = getDefaultCountryIso(context)
        val e164 = formatToE164(raw, countryIso)
        return cleanPhoneNumber(e164 ?: raw)
    }

    /**
     * Try to format a number to E.164 using device country ISO
     */
    private fun formatToE164(number: String, countryIso: String?): String? {
        if (number.isBlank() || countryIso.isNullOrBlank()) return null
        return try {
            PhoneNumberUtils.formatNumberToE164(number, countryIso)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Determine default country ISO (network -> SIM -> locale)
     */
    private fun getDefaultCountryIso(context: Context): String? {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val networkIso = tm.networkCountryIso?.trim()?.takeIf { it.isNotEmpty() }
            val simIso = tm.simCountryIso?.trim()?.takeIf { it.isNotEmpty() }
            val localeIso = Locale.getDefault().country?.trim()?.takeIf { it.isNotEmpty() }
            (networkIso ?: simIso ?: localeIso)?.uppercase(Locale.ROOT)
        } catch (e: Exception) {
            Locale.getDefault().country?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT)
        }
    }

    /**
     * WhatsApp contact data fallback (JID usually includes country code)
     */
    private fun getWhatsAppNumberFromContacts(context: Context, senderName: String): String? {
        return try {
            val uri = ContactsContract.Data.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.Data.MIMETYPE
            )
            
            // First try exact display name match
            val exactSelection = "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.Data.DISPLAY_NAME}=?"
            val exactArgs = arrayOf(
                "vnd.android.cursor.item/vnd.com.whatsapp.profile",
                senderName
            )
            
            if (queryWhatsAppProfile(context, uri, projection, exactSelection, exactArgs)?.let { return it } != null) {
                // return already handled
            }
            
            // Fallback to LIKE match
            val likeSelection = "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.Data.DISPLAY_NAME} LIKE ?"
            val likeArgs = arrayOf(
                "vnd.android.cursor.item/vnd.com.whatsapp.profile",
                "%$senderName%"
            )
            
            queryWhatsAppProfile(context, uri, projection, likeSelection, likeArgs)?.let { return it }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting WhatsApp number: ${e.message}")
            null
        }
    }

    private fun queryWhatsAppProfile(
        context: Context,
        uri: android.net.Uri,
        projection: Array<String>,
        selection: String,
        selectionArgs: Array<String>
    ): String? {
        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val dataIndex = cursor.getColumnIndex(ContactsContract.Data.DATA1)
            while (cursor.moveToNext()) {
                val data1 = if (dataIndex >= 0) cursor.getString(dataIndex) else null
                if (data1.isNullOrBlank()) continue
                
                val jidPart = data1.substringBefore("@")
                val digits = jidPart.replace(Regex("[^0-9]"), "")
                if (digits.length >= 10) {
                    return cleanPhoneNumber("+$digits")
                }
            }
        }
        return null
    }

    /**
     * Check if contacts permission is granted
     */
    private fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Normalize contact names for matching
     */
    private fun normalizeName(name: String): String {
        return name.lowercase().replace(Regex("[^\\p{L}\\p{Nd}]"), "")
    }

    /**
     * Check if contact name matches sender name (normalized)
     */
    private fun namesMatch(contactName: String, senderName: String, normalizedSender: String): Boolean {
        val normalizedContact = normalizeName(contactName)
        if (normalizedContact.isEmpty() || normalizedSender.isEmpty()) return false
        
        if (normalizedContact == normalizedSender) return true
        
        // Partial matching for cases like "John Doe" vs "John"
        if (normalizedContact.contains(normalizedSender) || normalizedSender.contains(normalizedContact)) {
            return true
        }
        
        return false
    }
    
    /**
     * Extract phone number from notification extras if available
     * Looks for phone number in notification metadata (not message text)
     */
    fun extractFromNotification(extras: android.os.Bundle): String? {
        Log.d(TAG, "========== EXTRACTING PHONE FROM NOTIFICATION ==========")
        
        // All possible notification fields to check
        val possibleKeys = listOf(
            // Standard Android notification fields
            "android.title",
            "android.text",
            "android.subText",
            "android.summaryText",
            "android.infoText",
            "android.bigText",
            "android.titleBig",
            "android.conversationTitle",
            
            // WhatsApp specific fields
            "android.messages",
            "android.messagingUser",
            "android.selfDisplayName",
            "android.remoteInputHistory",
            "android.template",
            "android.people",
            "android.person",
            
            // Additional fields
            "android.showWhen",
            "android.progress",
            "android.progressMax",
            "android.showChronometer",
            "android.chronometerCountDown",
            "android.largeIcon",
            "android.largeIcon.big",
            "android.reduced.images",
            "android.isGroupConversation"
        )
        
        // Log all available keys
        val allKeys = extras.keySet().toList()
        Log.d(TAG, "Total extras keys found: ${allKeys.size}")
        Log.d(TAG, "All keys: $allKeys")
        
        // Check each possible key
        for (key in possibleKeys) {
            val value = extras.get(key)
            if (value != null) {
                val valueStr = value.toString()
                Log.d(TAG, "[$key] = $valueStr")
                
                // Special handling for android.messagingUser (Person object)
                if (key == "android.messagingUser" && value is android.app.Person) {
                    val person = value as android.app.Person
                    val personKey = person.key
                    val personName = person.name?.toString()
                    Log.d(TAG, "Person key: $personKey, name: $personName")
                    
                    // Person key often contains phone number
                    if (personKey != null) {
                        val phoneWithPlus = Regex("\\+\\d{1,4}[\\s\\-]?\\d{6,14}").find(personKey)
                        if (phoneWithPlus != null) {
                            val extracted = phoneWithPlus.value.replace(Regex("[^0-9+]"), "")
                            Log.d(TAG, "✓✓✓ FOUND PHONE in Person.key: $extracted")
                            return extracted
                        }
                        
                        val phoneMatch = Regex("\\d{10,15}").find(personKey)
                        if (phoneMatch != null) {
                            Log.d(TAG, "✓✓✓ FOUND PHONE in Person.key: ${phoneMatch.value}")
                            return phoneMatch.value
                        }
                    }
                }
                
                // Try to extract phone number with country code first
                val phoneWithPlus = Regex("\\+\\d{1,4}[\\s\\-]?\\d{6,14}").find(valueStr)
                if (phoneWithPlus != null) {
                    val extracted = phoneWithPlus.value.replace(Regex("[^0-9+]"), "")
                    Log.d(TAG, "✓✓✓ FOUND PHONE WITH + from $key: $extracted")
                    return extracted
                }
                
                // Try to extract 10+ digit number
                val phoneMatch = Regex("\\d{10,15}").find(valueStr)
                if (phoneMatch != null) {
                    Log.d(TAG, "✓✓✓ FOUND PHONE from $key: ${phoneMatch.value}")
                    return phoneMatch.value
                }
            }
        }
        
        // Check ALL extras keys (not just known ones)
        Log.d(TAG, "Checking ALL extras keys for phone number...")
        for (key in allKeys) {
            val value = extras.get(key)
            if (value != null) {
                val valueStr = value.toString()
                
                // Skip already checked keys
                if (possibleKeys.contains(key)) continue
                
                Log.d(TAG, "[EXTRA: $key] = $valueStr")
                
                // Try to extract phone
                val phoneWithPlus = Regex("\\+\\d{1,4}[\\s\\-]?\\d{6,14}").find(valueStr)
                if (phoneWithPlus != null) {
                    val extracted = phoneWithPlus.value.replace(Regex("[^0-9+]"), "")
                    Log.d(TAG, "✓✓✓ FOUND PHONE in unknown field $key: $extracted")
                    return extracted
                }
                
                val phoneMatch = Regex("\\d{10,15}").find(valueStr)
                if (phoneMatch != null) {
                    Log.d(TAG, "✓✓✓ FOUND PHONE in unknown field $key: ${phoneMatch.value}")
                    return phoneMatch.value
                }
            }
        }
        
        Log.d(TAG, "❌ NO PHONE NUMBER FOUND in any notification field")
        Log.d(TAG, "========================================================")
        
        return null
    }
}
