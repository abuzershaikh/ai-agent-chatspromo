package com.message.bulksend.contactmanager

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.db.ContactEntity
import com.message.bulksend.db.ContactGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ContactsRepository(private val context: Context) {

    private val contactGroupDao = AppDatabase.getInstance(context).contactGroupDao()

    fun loadGroups(): Flow<List<Group>> {
        return contactGroupDao.getAllGroups().map { dbGroups ->
            // Get current subscription status
            val subscriptionInfo = com.message.bulksend.utils.SubscriptionUtils.getLocalSubscriptionInfo(context)
            val subscriptionType = subscriptionInfo["type"] as? String ?: "free"
            val isExpired = subscriptionInfo["isExpired"] as? Boolean ?: false
            val isPremiumActive = subscriptionType == "premium" && !isExpired

            dbGroups
                .filter { dbGroup ->
                    // Show all groups if premium is active
                    // Show only non-premium groups if free plan
                    if (isPremiumActive) {
                        true  // Show all groups
                    } else {
                        !dbGroup.isPremiumGroup  // Hide premium groups on free plan
                    }
                }
                .map { dbGroup ->
                    Group(
                        id = dbGroup.id,
                        name = dbGroup.name,
                        contacts = dbGroup.contacts.map { dbContact ->
                            Contact(
                                name = dbContact.name,
                                number = dbContact.number,
                                isWhatsApp = dbContact.isWhatsApp
                            )
                        },
                        timestamp = dbGroup.timestamp,
                        isPremiumGroup = dbGroup.isPremiumGroup
                    )
                }
        }
    }

    suspend fun saveGroup(groupName: String, contacts: List<Contact>): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Get subscription info
                val subscriptionInfo = com.message.bulksend.utils.SubscriptionUtils.getLocalSubscriptionInfo(context)
                val subscriptionType = subscriptionInfo["type"] as? String ?: "free"
                val isExpired = subscriptionInfo["isExpired"] as? Boolean ?: false
                val currentGroups = subscriptionInfo["currentGroups"] as? Int ?: 0
                val currentContacts = subscriptionInfo["currentContacts"] as? Int ?: 0
                val contactsLimit = subscriptionInfo["contactsLimit"] as? Int ?: 10
                val userEmail = subscriptionInfo["userEmail"] as? String ?: ""

                if (userEmail.isEmpty()) {
                    return@withContext Result.failure(Exception("User not logged in"))
                }

                // Check if premium is active
                val isPremiumActive = subscriptionType == "premium" && !isExpired

                // Limit contacts for FREE users only
                val limitedContacts = if (isPremiumActive) {
                    // Premium users: unlimited contacts (but limit to 20,000 for safety)
                    if (contacts.size > 20000) {
                        Log.w("ContactsRepository", "⚠️ Large contact list: ${contacts.size} contacts. Taking first 20,000 for performance.")
                        contacts.take(20000)
                    } else {
                        contacts
                    }
                } else {
                    // Free users: limit to 10 contacts total
                    val availableSlots = contactsLimit - currentContacts

                    if (availableSlots <= 0) {
                        return@withContext Result.failure(Exception(
                            "🚫 Contact limit reached!\n\n" +
                                    "Free plan: Maximum $contactsLimit contacts\n" +
                                    "Current: $currentContacts/$contactsLimit contacts\n\n" +
                                    "💎 Upgrade to Premium for unlimited contacts!"
                        ))
                    }

                    // Take only available slots
                    contacts.take(availableSlots)
                }

                if (limitedContacts.isEmpty()) {
                    return@withContext Result.failure(Exception(
                        "No contacts can be added!\n\n" +
                                "Contact limit already reached."
                    ))
                }

                Log.d("ContactsRepository", "💾 Saving ${limitedContacts.size} contacts in batches...")

                // Save the group with limited contacts in batches to prevent memory issues
                val contactEntities = mutableListOf<ContactEntity>()
                val batchSize = 1000

                // Process contacts in batches to avoid memory issues
                limitedContacts.chunked(batchSize).forEachIndexed { index, batch ->
                    Log.d("ContactsRepository", "Processing batch ${index + 1}/${(limitedContacts.size + batchSize - 1) / batchSize}")
                    batch.forEach {
                        contactEntities.add(ContactEntity(name = it.name, number = it.number, isWhatsApp = it.isWhatsApp))
                    }
                }

                // Mark if this is a premium group (created during premium plan)
                val isPremiumGroup = isPremiumActive

                val group = ContactGroup(
                    name = groupName,
                    contacts = contactEntities,
                    timestamp = System.currentTimeMillis(),
                    isPremiumGroup = isPremiumGroup  // Mark if created during premium
                )

                Log.d("ContactsRepository", "💾 Inserting group into database...")
                contactGroupDao.insertGroup(group)

                // Update local preferences
                val newContactCount = currentContacts + limitedContacts.size
                val newGroupCount = currentGroups + 1

                val sharedPref = context.getSharedPreferences("subscription_prefs", android.content.Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putInt("current_contacts", newContactCount)
                    putInt("current_groups", newGroupCount)
                    apply()
                }

                android.util.Log.d("ContactsRepository", "✅ Group saved:")
                android.util.Log.d("ContactsRepository", "  Name: $groupName")
                android.util.Log.d("ContactsRepository", "  Contacts: ${limitedContacts.size}")
                android.util.Log.d("ContactsRepository", "  Premium Group: $isPremiumGroup")

                // Update Firebase in background (async, non-blocking)
                try {
                    val userManager = com.message.bulksend.auth.UserManager(context)
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        userManager.updateContactCount(userEmail, newContactCount)
                        userManager.updateGroupCount(userEmail, newGroupCount)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ContactsRepository", "Firebase update failed (non-critical): ${e.message}")
                }

                // Success message
                val message = if (limitedContacts.size < contacts.size) {
                    "Group saved with ${limitedContacts.size} contacts!\n\n" +
                            "⚠️ ${contacts.size - limitedContacts.size} contacts were skipped due to free plan limit (max $contactsLimit).\n\n" +
                            "💎 Upgrade to Premium for unlimited contacts!"
                } else {
                    "✅ Group saved successfully! Added ${limitedContacts.size} contacts."
                }

                Result.success(message)

            } catch (e: Exception) {
                Log.e("ContactsRepository", "Error saving group", e)
                Result.failure(e)
            }
        }
    }

    suspend fun deleteGroup(groupId: Long) {
        withContext(Dispatchers.IO) {
            // Function ka naam theek kiya gaya
            contactGroupDao.deleteGroup(groupId)
        }
    }
    
    suspend fun deleteContactFromGroup(groupId: Long, contactNumber: String) {
        withContext(Dispatchers.IO) {
            try {
                // Get the current group
                val group = contactGroupDao.getGroupById(groupId)
                if (group != null) {
                    // Filter out the contact to delete
                    val updatedContacts = group.contacts.filter { it.number != contactNumber }
                    
                    // Update the group with remaining contacts
                    val updatedGroup = group.copy(contacts = updatedContacts)
                    contactGroupDao.updateGroup(updatedGroup)
                    
                    // Update contact count in preferences
                    val sharedPref = context.getSharedPreferences("subscription_prefs", android.content.Context.MODE_PRIVATE)
                    val currentContacts = sharedPref.getInt("current_contacts", 0)
                    if (currentContacts > 0) {
                        with(sharedPref.edit()) {
                            putInt("current_contacts", currentContacts - 1)
                            apply()
                        }
                    }
                    
                    Log.d("ContactsRepository", "✅ Contact deleted from group: $contactNumber")
                }
            } catch (e: Exception) {
                Log.e("ContactsRepository", "Error deleting contact from group", e)
            }
        }
    }

    suspend fun addContactsToGroup(groupId: Long, newContacts: List<Contact>): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Get subscription info
                val subscriptionInfo = com.message.bulksend.utils.SubscriptionUtils.getLocalSubscriptionInfo(context)
                val subscriptionType = subscriptionInfo["type"] as? String ?: "free"
                val isExpired = subscriptionInfo["isExpired"] as? Boolean ?: false
                val currentContacts = subscriptionInfo["currentContacts"] as? Int ?: 0
                val contactsLimit = subscriptionInfo["contactsLimit"] as? Int ?: 10
                val userEmail = subscriptionInfo["userEmail"] as? String ?: ""

                if (userEmail.isEmpty()) {
                    return@withContext Result.failure(Exception("User not logged in"))
                }

                // Check if premium is active
                val isPremiumActive = subscriptionType == "premium" && !isExpired

                // Get the current group
                val group = contactGroupDao.getGroupById(groupId)
                if (group == null) {
                    return@withContext Result.failure(Exception("Group not found"))
                }

                // Get existing contact numbers to avoid duplicates
                val existingNumbers = group.contacts.map { it.number }.toSet()
                val uniqueNewContacts = newContacts.filter { it.number !in existingNumbers }

                if (uniqueNewContacts.isEmpty()) {
                    return@withContext Result.failure(Exception("All contacts already exist in this group"))
                }

                // Limit contacts for FREE users only
                val contactsToAdd = if (isPremiumActive) {
                    // Premium users: unlimited contacts (but limit to 20,000 for safety)
                    if (uniqueNewContacts.size > 20000) {
                        Log.w("ContactsRepository", "⚠️ Large contact list: ${uniqueNewContacts.size} contacts. Taking first 20,000 for performance.")
                        uniqueNewContacts.take(20000)
                    } else {
                        uniqueNewContacts
                    }
                } else {
                    // Free users: check total limit
                    val availableSlots = contactsLimit - currentContacts

                    if (availableSlots <= 0) {
                        return@withContext Result.failure(Exception(
                            "🚫 Contact limit reached!\n\n" +
                                    "Free plan: Maximum $contactsLimit contacts\n" +
                                    "Current: $currentContacts/$contactsLimit contacts\n\n" +
                                    "💎 Upgrade to Premium for unlimited contacts!"
                        ))
                    }

                    // Take only available slots
                    uniqueNewContacts.take(availableSlots)
                }

                if (contactsToAdd.isEmpty()) {
                    return@withContext Result.failure(Exception(
                        "No contacts can be added!\n\n" +
                                "Contact limit already reached."
                    ))
                }

                // Convert to ContactEntity and add to existing contacts
                val newContactEntities = contactsToAdd.map { 
                    ContactEntity(name = it.name, number = it.number, isWhatsApp = it.isWhatsApp) 
                }
                val updatedContacts = group.contacts + newContactEntities

                // Update the group
                val updatedGroup = group.copy(contacts = updatedContacts)
                contactGroupDao.updateGroup(updatedGroup)

                // Update contact count in preferences
                val newContactCount = currentContacts + contactsToAdd.size
                val sharedPref = context.getSharedPreferences("subscription_prefs", android.content.Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putInt("current_contacts", newContactCount)
                    apply()
                }

                Log.d("ContactsRepository", "✅ Added ${contactsToAdd.size} contacts to group: ${group.name}")

                // Update Firebase in background (async, non-blocking)
                try {
                    val userManager = com.message.bulksend.auth.UserManager(context)
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        userManager.updateContactCount(userEmail, newContactCount)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ContactsRepository", "Firebase update failed (non-critical): ${e.message}")
                }

                // Success message
                val message = if (contactsToAdd.size < uniqueNewContacts.size) {
                    "Added ${contactsToAdd.size} contacts to '${group.name}'!\n\n" +
                            "⚠️ ${uniqueNewContacts.size - contactsToAdd.size} contacts were skipped due to free plan limit (max $contactsLimit).\n\n" +
                            "💎 Upgrade to Premium for unlimited contacts!"
                } else {
                    val duplicateCount = newContacts.size - uniqueNewContacts.size
                    val duplicateMsg = if (duplicateCount > 0) "\n($duplicateCount duplicates skipped)" else ""
                    "✅ Added ${contactsToAdd.size} contacts to '${group.name}'!$duplicateMsg"
                }

                Result.success(message)

            } catch (e: Exception) {
                Log.e("ContactsRepository", "Error adding contacts to group", e)
                Result.failure(e)
            }
        }
    }

    fun parseCsv(context: Context, uri: Uri): List<Contact> {
        val contacts = mutableListOf<Contact>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var isFirstLine = true
                    reader.forEachLine { line ->
                        if (line.trim().isNotEmpty()) {
                            // Skip header if it contains common header words
                            if (isFirstLine && (line.lowercase().contains("name") || line.lowercase().contains("phone") || line.lowercase().contains("number"))) {
                                isFirstLine = false
                                return@forEachLine
                            }
                            isFirstLine = false

                            // Handle both comma and semicolon separators
                            val separator = if (line.contains(";")) ";" else ","
                            val tokens = line.split(separator).map { it.trim().replace("\"", "") }

                            if (tokens.size >= 2) {
                                val name = tokens[0].trim()
                                val number = tokens[1].trim().replace(Regex("[^0-9+]"), "")
                                if (name.isNotBlank() && number.isNotBlank() && number.length >= 7) {
                                    contacts.add(Contact(name, number, isWhatsApp = false))
                                }
                            } else if (tokens.size == 1) {
                                // Handle single column with name and number in one field
                                val parts = tokens[0].split(Regex("\\s+"))
                                if (parts.size >= 2) {
                                    val number = parts.last().replace(Regex("[^0-9+]"), "")
                                    if (number.length >= 7) {
                                        val name = parts.dropLast(1).joinToString(" ")
                                        contacts.add(Contact(name.ifBlank { "Unknown" }, number, isWhatsApp = false))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ContactsRepository", "CSV parse karne mein error", e)
        }
        return contacts
    }

    fun parseVcf(context: Context, uri: Uri): List<Contact> {
        val contacts = mutableListOf<Contact>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var currentName: String? = null
                    var currentNumber: String? = null
                    reader.forEachLine { line ->
                        when {
                            line.startsWith("FN:") -> currentName = line.substring(3).trim()
                            line.startsWith("TEL") -> {
                                currentNumber = line.substring(line.indexOf(":") + 1)
                                    .trim()
                                    .replace(Regex("[^0-9+]"), "")
                            }
                            line == "END:VCARD" -> {
                                if (currentName != null && currentNumber != null) {
                                    contacts.add(Contact(currentName!!, currentNumber!!, isWhatsApp = false))
                                }
                                currentName = null
                                currentNumber = null
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ContactsRepository", "VCF parse karne mein error", e)
        }
        return contacts
    }

    fun parseXlsx(uri: Uri): List<Contact> {
        val contacts = mutableListOf<Contact>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0)

                for (i in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(i)
                    val nameCell = row?.getCell(0)
                    val numberCell = row?.getCell(1)

                    val name = nameCell?.stringCellValue?.trim() ?: ""

                    val number = when (numberCell?.cellType) {
                        CellType.NUMERIC -> numberCell.numericCellValue.toLong().toString()
                        CellType.STRING -> numberCell.stringCellValue.trim().replace(Regex("[^0-9+]"), "")
                        else -> ""
                    }

                    if (name.isNotBlank() && number.isNotBlank()) {
                        contacts.add(Contact(name, number, isWhatsApp = false))
                    }
                }
                workbook.close()
            }
        } catch (e: Exception) {
            Log.e("ContactsRepository", "XLSX file parse karne mein error", e)
        }
        return contacts
    }

    fun parseCommaSeparatedText(text: String): List<Contact> {
        val contacts = mutableListOf<Contact>()
        text.lines().forEach { line ->
            if (line.trim().isNotEmpty()) {
                // Handle both comma and semicolon separators
                val separator = if (line.contains(";")) ";" else ","
                val parts = line.split(separator).map { it.trim() }

                if (parts.size >= 2) {
                    val name = parts[0].trim()
                    val number = parts[1].trim().replace(Regex("[^0-9+]"), "")
                    if (name.isNotBlank() && number.isNotBlank() && number.length >= 7) {
                        contacts.add(Contact(name, number, isWhatsApp = false))
                    }
                } else if (parts.size == 1) {
                    // Try to extract name and number from single field
                    val singlePart = parts[0].trim()
                    val tokens = singlePart.split(Regex("\\s+"))
                    if (tokens.size >= 2) {
                        val number = tokens.last().replace(Regex("[^0-9+]"), "")
                        if (number.length >= 7) {
                            val name = tokens.dropLast(1).joinToString(" ")
                            contacts.add(Contact(name.ifBlank { "Unknown" }, number, isWhatsApp = false))
                        }
                    }
                }
            }
        }
        return contacts
    }

    @SuppressLint("Range")
    suspend fun getWhatsAppContacts(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }): List<Contact> = withContext(Dispatchers.IO) {
        val whatsappContacts = mutableListOf<Contact>()
        val contentResolver = context.contentResolver
        val startTime = System.currentTimeMillis()

        try {
            val uri = ContactsContract.Data.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.Data.MIMETYPE
            )

            // Check which WhatsApp is installed and prefer regular WhatsApp over Business
            val whatsappMimeTypes = listOf(
                "vnd.android.cursor.item/vnd.com.whatsapp.profile",
                "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
            )
            val whatsappBusinessMimeTypes = listOf(
                "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile",
                "vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call"
            )

            // First check if regular WhatsApp is available
            val testCursor = contentResolver.query(
                uri,
                arrayOf(ContactsContract.Data.MIMETYPE),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(whatsappMimeTypes[0]),
                null
            )

            val hasWhatsApp = testCursor?.use { it.count > 0 } ?: false
            testCursor?.close()

            // Use WhatsApp if available, otherwise use WhatsApp Business
            val mimeTypesToUse = if (hasWhatsApp) {
                Log.d("ContactsRepository", "📱 Using regular WhatsApp MIME types")
                whatsappMimeTypes
            } else {
                Log.d("ContactsRepository", "💼 Using WhatsApp Business MIME types")
                whatsappBusinessMimeTypes
            }

            val selection = "${ContactsContract.Data.MIMETYPE} IN (?, ?)"
            val selectionArgs = mimeTypesToUse.toTypedArray()

            val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                val contactIdIndex = it.getColumnIndex(ContactsContract.Data.CONTACT_ID)

                // Check if columns exist
                if (nameIndex == -1 || contactIdIndex == -1) {
                    Log.e("ContactsRepository", "Required columns not found in cursor")
                    return@withContext whatsappContacts
                }

                val totalCount = it.count
                Log.d("ContactsRepository", "📱 Found $totalCount WhatsApp contacts to process")

                // Report initial progress
                onProgress(0, totalCount)

                var processedCount = 0
                val processedContactIds = mutableSetOf<String>() // To avoid duplicate queries

                while (it.moveToNext()) {
                    try {
                        val name = it.getString(nameIndex) ?: continue
                        val contactId = it.getString(contactIdIndex) ?: continue

                        // Skip if already processed this contact ID
                        if (processedContactIds.contains(contactId)) {
                            continue
                        }
                        processedContactIds.add(contactId)

                        var number = ""

                        val phoneCursor = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId),
                            null
                        )

                        phoneCursor?.use { pCursor ->
                            val numberIndex = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (numberIndex != -1 && pCursor.moveToFirst()) {
                                number = pCursor.getString(numberIndex) ?: ""
                            }
                        }

                        if (name.isNotBlank() && number.isNotBlank()) {
                            // Clean and format number with country code
                            var cleanNumber = number.replace(Regex("[^0-9+]"), "")

                            // Add country code if not present
                            if (!cleanNumber.startsWith("+")) {
                                // If number starts with 0, remove it and add country code
                                if (cleanNumber.startsWith("0")) {
                                    cleanNumber = cleanNumber.substring(1)
                                }
                                // Add default country code (India +91) if number doesn't have country code
                                // You can change this based on your region
                                if (cleanNumber.length == 10) {
                                    cleanNumber = "+91$cleanNumber"
                                } else if (cleanNumber.length > 10 && !cleanNumber.startsWith("+")) {
                                    cleanNumber = "+$cleanNumber"
                                }
                            }

                            whatsappContacts.add(Contact(name, cleanNumber, isWhatsApp = true))
                            processedCount++

                            // Report progress every 100 contacts
                            if (processedCount % 100 == 0) {
                                onProgress(processedCount, totalCount)
                                Log.d("ContactsRepository", "📊 Processed $processedCount/$totalCount contacts...")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ContactsRepository", "Error processing contact: ${e.message}")
                        continue
                    }
                }

                val elapsedTime = System.currentTimeMillis() - startTime
                Log.d("ContactsRepository", "✅ Loaded $processedCount WhatsApp contacts in ${elapsedTime}ms")

                // Report final progress
                onProgress(processedCount, totalCount)
            }
        } catch (e: Exception) {
            Log.e("ContactsRepository", "Error fetching WhatsApp contacts", e)
        }

        // Remove duplicates by number (handles WhatsApp + WhatsApp Business duplicates)
        val uniqueContacts = whatsappContacts.distinctBy { it.number }
        Log.d("ContactsRepository", "📋 Returning ${uniqueContacts.size} unique contacts (removed ${whatsappContacts.size - uniqueContacts.size} duplicates)")

        // Report completion
        onProgress(uniqueContacts.size, uniqueContacts.size)

        return@withContext uniqueContacts
    }

    suspend fun getTotalContactsCount(): Int = withContext(Dispatchers.IO) {
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                null,
                null,
                null
            )
            val count = cursor?.count ?: 0
            cursor?.close()
            return@withContext count
        } catch (e: Exception) {
            Log.e("ContactsRepository", "Kul contacts ginne mein error", e)
            return@withContext 0
        }
    }

    suspend fun fetchFromGoogleSheets(sheetUrl: String): List<Contact> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<Contact>()
        try {
            // Validate URL first
            if (!sheetUrl.contains("docs.google.com/spreadsheets")) {
                throw Exception("Invalid Google Sheets URL. Please use a valid Google Sheets link.")
            }
            
            // Convert Google Sheets URL to CSV export URL
            val csvUrl = convertToCSVUrl(sheetUrl)
            Log.d("ContactsRepository", "Fetching from: $csvUrl")

            val url = URL(csvUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            
            // Add headers to mimic browser request
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.setRequestProperty("Accept", "text/csv,text/plain,*/*")

            val responseCode = connection.responseCode
            Log.d("ContactsRepository", "Response code: $responseCode")
            
            when (responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { reader ->
                        var isFirstLine = true
                        var lineCount = 0
                        var nameColumnIndex = -1
                        var phoneColumnIndex = -1
                        
                        reader.forEachLine { line ->
                            lineCount++
                            if (line.trim().isNotEmpty()) {
                                val tokens = parseCSVLine(line)
                                
                                // First line - detect header columns
                                if (isFirstLine) {
                                    isFirstLine = false
                                    
                                    // Find Name and Phone columns by header
                                    tokens.forEachIndexed { index, header ->
                                        val lowerHeader = header.lowercase().trim()
                                        when {
                                            // Name column detection
                                            nameColumnIndex == -1 && (
                                                lowerHeader == "name" ||
                                                lowerHeader.contains("name") ||
                                                lowerHeader == "contact" ||
                                                lowerHeader == "customer"
                                            ) -> nameColumnIndex = index
                                            
                                            // Phone column detection
                                            phoneColumnIndex == -1 && (
                                                lowerHeader == "phone" ||
                                                lowerHeader == "phone number" ||
                                                lowerHeader == "phonenumber" ||
                                                lowerHeader.contains("phone") ||
                                                lowerHeader == "mobile" ||
                                                lowerHeader == "number" ||
                                                lowerHeader == "contact number" ||
                                                lowerHeader == "cell" ||
                                                lowerHeader == "whatsapp"
                                            ) -> phoneColumnIndex = index
                                        }
                                    }
                                    
                                    Log.d("ContactsRepository", "Header detected - Name col: $nameColumnIndex, Phone col: $phoneColumnIndex")
                                    Log.d("ContactsRepository", "Headers: $tokens")
                                    
                                    // If no header found, assume first row is data with col 0=name, col 1=phone
                                    if (nameColumnIndex == -1 && phoneColumnIndex == -1) {
                                        // Check if first row looks like data (has a phone number)
                                        val possiblePhone = tokens.find { 
                                            it.replace(Regex("[^0-9]"), "").length >= 7 
                                        }
                                        if (possiblePhone != null) {
                                            // First row is data, use default columns
                                            nameColumnIndex = 0
                                            phoneColumnIndex = 1
                                            Log.d("ContactsRepository", "No header found, using default columns")
                                            // Process this row as data
                                            processRowAsContact(tokens, nameColumnIndex, phoneColumnIndex, contacts)
                                        }
                                    }
                                    return@forEachLine
                                }

                                // Process data rows
                                processRowAsContact(tokens, nameColumnIndex, phoneColumnIndex, contacts)
                            }
                        }
                        Log.d("ContactsRepository", "Processed $lineCount lines, found ${contacts.size} contacts")
                    }
                }
                HttpURLConnection.HTTP_MOVED_TEMP, HttpURLConnection.HTTP_MOVED_PERM, 307, 308 -> {
                    val newUrl = connection.getHeaderField("Location")
                    Log.d("ContactsRepository", "Redirect to: $newUrl")
                    throw Exception("Sheet requires sign-in. Make sure the sheet is publicly accessible (Anyone with the link can view).")
                }
                HttpURLConnection.HTTP_FORBIDDEN, HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    throw Exception("Access denied. Make sure the Google Sheet is publicly accessible:\n1. Open sheet in browser\n2. Click Share\n3. Change to 'Anyone with the link'")
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    throw Exception("Sheet not found. Please check the URL is correct.")
                }
                else -> {
                    throw Exception("Failed to fetch data. Response code: $responseCode")
                }
            }
            connection.disconnect()
        } catch (e: java.net.UnknownHostException) {
            Log.e("ContactsRepository", "Network error", e)
            throw Exception("No internet connection. Please check your network.")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("ContactsRepository", "Timeout error", e)
            throw Exception("Connection timed out. Please try again.")
        } catch (e: Exception) {
            Log.e("ContactsRepository", "Google Sheets fetch error", e)
            throw e
        }
        return@withContext contacts
    }
    
    private fun processRowAsContact(
        tokens: List<String>,
        nameColumnIndex: Int,
        phoneColumnIndex: Int,
        contacts: MutableList<Contact>
    ) {
        // Get name from detected column or find any text column
        var name = if (nameColumnIndex >= 0 && nameColumnIndex < tokens.size) {
            tokens[nameColumnIndex].trim()
        } else {
            // Try to find a name-like value (text without too many numbers)
            tokens.find { token ->
                val cleaned = token.trim()
                cleaned.isNotBlank() && 
                cleaned.replace(Regex("[^0-9]"), "").length < cleaned.length / 2
            }?.trim() ?: ""
        }
        
        // Get phone from detected column or find any phone-like value
        var phone = if (phoneColumnIndex >= 0 && phoneColumnIndex < tokens.size) {
            tokens[phoneColumnIndex].trim()
        } else {
            // Try to find a phone-like value (mostly numbers, 7+ digits)
            tokens.find { token ->
                val digits = token.replace(Regex("[^0-9]"), "")
                digits.length >= 7
            }?.trim() ?: ""
        }
        
        // Clean phone number
        phone = phone.replace(Regex("[^0-9+]"), "")
        
        // If name is empty but we have phone, use a default name
        if (name.isBlank() && phone.length >= 7) {
            name = "Contact"
        }
        
        // Add contact if valid
        if (name.isNotBlank() && phone.length >= 7) {
            contacts.add(Contact(name, phone, isWhatsApp = false))
            Log.d("ContactsRepository", "Added contact: $name -> $phone")
        }
    }
    
    // Helper function to parse CSV line with quoted fields
    private fun parseCSVLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    tokens.add(current.toString().trim().replace("\"", ""))
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        tokens.add(current.toString().trim().replace("\"", ""))
        
        return tokens
    }

    private fun convertToCSVUrl(sheetUrl: String): String {
        // Clean the URL first
        val cleanUrl = sheetUrl.trim()
        
        // Extract sheet ID from various URL formats
        val sheetIdRegex = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)")
        val sheetIdMatch = sheetIdRegex.find(cleanUrl)
        val sheetId = sheetIdMatch?.groupValues?.get(1)
        
        if (sheetId == null) {
            Log.e("ContactsRepository", "Could not extract sheet ID from URL: $cleanUrl")
            return cleanUrl
        }
        
        // Extract gid (sheet tab ID) if present
        val gidRegex = Regex("[#&?]gid=([0-9]+)")
        val gidMatch = gidRegex.find(cleanUrl)
        val gid = gidMatch?.groupValues?.get(1) ?: "0"
        
        // Use gviz/tq format which works better for public sheets
        val exportUrl = "https://docs.google.com/spreadsheets/d/$sheetId/gviz/tq?tqx=out:csv&gid=$gid"
        Log.d("ContactsRepository", "Converted URL: $cleanUrl -> $exportUrl")
        
        return exportUrl
    }
}

