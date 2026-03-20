package com.message.bulksend.autorespond.tools.sheetconnect

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Manager for handling Google Sheets API via Cloudflare worker
 */
class SheetConnectManager(private val context: Context) {

    private val tag = "SheetConnectManager"

    // Set to your Cloudflare Worker URL
    var workerBaseUrl = "https://google-sheet-worker.aawuazer.workers.dev"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance("chatspromoweb")
    }

    private val auth = FirebaseAuth.getInstance()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    private data class GoogleSheetTokenConfig(
        val clientId: String,
        val clientSecret: String,
        val accessToken: String,
        val refreshToken: String,
        val expiry: Long
    )

    // ─── SETUP & CONFIG ──────────────────────────────────────────────────────

    /**
     * Start the OAuth Flow
     * Calls Worker /auth/login with clientId and secret to get Google OAuth URL.
     */
    suspend fun initiateOAuthLogin(clientId: String, clientSecret: String): Result<String> {
        return try {
            val uid = currentUserId
            if (uid.isBlank()) return Result.failure(Exception("User not logged in."))

            val body = JSONObject().apply {
                put("userId", uid)
                put("clientId", clientId)
                put("clientSecret", clientSecret)
            }.toString().toRequestBody(jsonType)

            val request = Request.Builder()
                .url("$workerBaseUrl/auth/login")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }.use { response ->
                val responseBody = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val authUrl = json.optString("url")
                    if (authUrl.isNotBlank()) {
                        Result.success(authUrl)
                    } else {
                        Result.failure(Exception("Failed to get Auth URL. Response: $responseBody"))
                    }
                } else {
                    Result.failure(Exception("HTTP Error: ${response.code} $responseBody"))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "initiateOAuthLogin Error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Config check from Firestore
     */
    suspend fun getConfig(): SheetConfig? {
        return try {
            val uid = currentUserId
            if (uid.isBlank()) return null

            val doc = firestore
                .collection("users_config")
                .document(uid)
                .get()
                .await()

            if (!doc.exists()) return null

            SheetConfig(
                uid = uid,
                sheetApiConnected = doc.getBoolean("sheetApiConnected") ?: false,
                sheetApiLastSetup = doc.getString("sheetApiLastSetup") ?: "",
                googleSheetConfig = doc.getString("googleSheetConfig") ?: ""
            )
        } catch (e: Exception) {
            Log.e(tag, "Get config error: ${e.message}", e)
            null
        }
    }

    suspend fun isSetupDone(): Boolean {
        val config = getConfig()
        return config != null && config.sheetApiConnected
    }

    /**
     * Get Mapping Config from Firestore
     */
    suspend fun getMappingConfig(): SheetMappingConfig? {
        return try {
            val uid = currentUserId
            if (uid.isBlank()) return null

            val doc = firestore
                .collection("users_config")
                .document(uid)
                .get()
                .await()

            if (!doc.exists()) return null

            val data = doc.data ?: return null
            SheetMappingConfig(
                spreadsheetUrlId =
                    (data["sheetMappingUrl"] as? String)
                        ?: (data["sheetUrl"] as? String)
                        ?: (data["spreadsheetUrl"] as? String)
                        ?: "",
                spreadsheetId =
                    (data["sheetMappingId"] as? String)
                        ?: (data["sheetId"] as? String)
                        ?: (data["spreadsheetId"] as? String)
                        ?: "",
                sheetName =
                    (data["sheetMappingName"] as? String)
                        ?: (data["sheetName"] as? String)
                        ?: "",
                nameColumn =
                    (data["sheetMappingColName"] as? String)
                        ?: (data["nameColumn"] as? String)
                        ?: "",
                phoneColumn =
                    (data["sheetMappingColPhone"] as? String)
                        ?: (data["phoneColumn"] as? String)
                        ?: "",
                emailColumn =
                    (data["sheetMappingColEmail"] as? String)
                        ?: (data["emailColumn"] as? String)
                        ?: "",
                notesColumn =
                    (data["sheetMappingColNotes"] as? String)
                        ?: (data["notesColumn"] as? String)
                        ?: ""
            )
        } catch (e: Exception) {
            Log.e(tag, "Get mapping config error: ${e.message}", e)
            null
        }
    }

    /**
     * Save Mapping Config to Firestore
     */
    suspend fun saveMappingConfig(config: SheetMappingConfig): Result<Unit> {
        return try {
            val uid = currentUserId
            if (uid.isBlank()) return Result.failure(Exception("User not logged in."))

            val updates = mapOf(
                "sheetMappingUrl" to config.spreadsheetUrlId,
                "sheetMappingId" to config.spreadsheetId,
                "sheetMappingName" to config.sheetName,
                "sheetMappingColName" to config.nameColumn,
                "sheetMappingColPhone" to config.phoneColumn,
                "sheetMappingColEmail" to config.emailColumn,
                "sheetMappingColNotes" to config.notesColumn
            )

            firestore.collection("users_config").document(uid)
                .set(updates, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Save mapping config error: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ─── SHEET API ───────────────────────────────────────────────────────────

    /**
     * Create a new Google Sheet with the given title.
     * Returns the new spreadsheetId and URL.
     */
    suspend fun createSheet(title: String): Result<Pair<String, String>> {
        return try {
            val uid = currentUserId
            if (uid.isBlank()) return Result.failure(Exception("User not logged in."))

            val body = JSONObject().apply {
                put("userId", uid)
                put("title", title)
            }.toString().toRequestBody(jsonType)

            val request = Request.Builder()
                .url("$workerBaseUrl/sheet/create")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }.use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val spreadsheetId = json.optString("spreadsheetId")
                    val spreadsheetUrl = json.optString("spreadsheetUrl")
                    if (spreadsheetId.isNotBlank()) {
                        Result.success(Pair(spreadsheetId, spreadsheetUrl))
                    } else {
                        Result.failure(Exception("Create succeeded but no spreadsheetId returned."))
                    }
                } else {
                    Result.failure(Exception("HTTP Error: ${response.code} $responseBody"))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "createSheet error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Save a newly created spreadsheet record to Firestore so it appears in "My Spreadsheets".
     */
    suspend fun saveCreatedSheet(sheet: CreatedSheet) {
        try {
            val uid = currentUserId
            if (uid.isBlank()) return

            val existing = getCreatedSheets().toMutableList()
            if (existing.none { it.spreadsheetId == sheet.spreadsheetId }) {
                existing.add(0, sheet) // newest first
            }

            val arr = JSONArray()
            existing.forEach { s ->
                arr.put(JSONObject().apply {
                    put("title", s.title)
                    put("spreadsheetId", s.spreadsheetId)
                    put("spreadsheetUrl", s.spreadsheetUrl)
                    put("createdAt", s.createdAt)
                })
            }

            firestore.collection("users_config").document(uid)
                .set(mapOf("createdSheetsJson" to arr.toString()), com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(tag, "saveCreatedSheet error: ${e.message}", e)
        }
    }

    /**
     * Load previously created spreadsheets for this user.
     */
    suspend fun getCreatedSheets(): List<CreatedSheet> {
        return try {
            val uid = currentUserId
            if (uid.isBlank()) return emptyList()

            val doc = firestore.collection("users_config").document(uid).get().await()
            val json = doc.getString("createdSheetsJson") ?: return emptyList()

            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CreatedSheet(
                    title          = obj.optString("title"),
                    spreadsheetId  = obj.optString("spreadsheetId"),
                    spreadsheetUrl = obj.optString("spreadsheetUrl"),
                    createdAt      = obj.optString("createdAt")
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "getCreatedSheets error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchSheetMetadata(spreadsheetUrlOrId: String): Result<SheetMetadataResponse> {
        val direct = fetchSheetMetadataDirect(spreadsheetUrlOrId)
        val directHasSheets = direct.getOrNull()?.sheets?.isNotEmpty() == true
        if (direct.isSuccess && directHasSheets) return direct

        val first = fetchSheetMetadataOnce(spreadsheetUrlOrId)
        val firstHasSheets = first.getOrNull()?.sheets?.isNotEmpty() == true
        if (first.isSuccess && firstHasSheets) return first

        val extractedId = extractSpreadsheetId(spreadsheetUrlOrId)
        if (!extractedId.isNullOrBlank() && !extractedId.equals(spreadsheetUrlOrId.trim(), ignoreCase = true)) {
            val second = fetchSheetMetadataOnce(extractedId)
            val secondHasSheets = second.getOrNull()?.sheets?.isNotEmpty() == true
            if (second.isSuccess && secondHasSheets) return second
            if (first.isFailure) return second
        }
        return if (first.isSuccess) first else direct
    }

    private suspend fun fetchSheetMetadataOnce(spreadsheetUrlOrId: String): Result<SheetMetadataResponse> {
        return try {
            val uid = currentUserId
            if (uid.isBlank()) return Result.failure(Exception("User not logged in."))

            val body = JSONObject().apply {
                put("userId", uid)
                put("spreadsheetUrlOrId", spreadsheetUrlOrId)
            }.toString().toRequestBody(jsonType)

            val request = Request.Builder()
                .url("$workerBaseUrl/sheet/metadata")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }.use { response ->
                val responseBody = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val success = json.optBoolean("success", true)
                    val sheetsArray =
                        json.optJSONArray("sheets")
                            ?: json.optJSONArray("sheetTabs")
                            ?: json.optJSONObject("data")?.optJSONArray("sheets")
                            ?: json.optJSONObject("data")?.optJSONArray("sheetTabs")
                            ?: json.optJSONObject("spreadsheet")?.optJSONArray("sheets")
                    val sheetNamesArray =
                        json.optJSONArray("sheetNames")
                            ?: json.optJSONObject("data")?.optJSONArray("sheetNames")
                    val sheetList = mutableListOf<SheetInfo>()

                    if (sheetsArray != null) {
                        for (i in 0 until sheetsArray.length()) {
                            val sheetEntry = sheetsArray.opt(i)
                            when (sheetEntry) {
                                is JSONObject -> {
                                    val propertiesObj = sheetEntry.optJSONObject("properties")
                                    val name =
                                        sheetEntry.optString("sheetName")
                                            .ifBlank { sheetEntry.optString("title") }
                                            .ifBlank { sheetEntry.optString("name") }
                                            .ifBlank { propertiesObj?.optString("title").orEmpty() }
                                    val colsArray =
                                        sheetEntry.optJSONArray("columns")
                                            ?: sheetEntry.optJSONArray("headers")
                                            ?: sheetEntry.optJSONObject("meta")?.optJSONArray("columns")
                                    val colsList = mutableListOf<String>()
                                    if (colsArray != null) {
                                        for (j in 0 until colsArray.length()) {
                                            colsList.add(colsArray.optString(j).trim())
                                        }
                                    }
                                    if (name.isNotBlank()) {
                                        sheetList.add(SheetInfo(name, colsList.filter { it.isNotBlank() }))
                                    }
                                }
                                else -> {
                                    val rawName = sheetEntry?.toString()?.trim().orEmpty()
                                    if (rawName.isNotBlank() && !rawName.equals("null", ignoreCase = true)) {
                                        sheetList.add(SheetInfo(rawName, emptyList()))
                                    }
                                }
                            }
                        }
                    } else if (sheetNamesArray != null) {
                        for (i in 0 until sheetNamesArray.length()) {
                            val name = sheetNamesArray.optString(i).trim()
                            if (name.isNotBlank()) {
                                sheetList.add(SheetInfo(name, emptyList()))
                            }
                        }
                    }

                    Log.d(tag, "fetchSheetMetadata parsed sheets=${sheetList.size} source='${spreadsheetUrlOrId.take(120)}'")

                    Result.success(
                        SheetMetadataResponse(
                            success = success,
                            spreadsheetId = json.optString("spreadsheetId"),
                            spreadsheetTitle =
                                json.optString("spreadsheetTitle")
                                    .ifBlank { json.optString("title") },
                            sheets = sheetList,
                            error = json.optString("error")
                        )
                    )
                } else {
                    Result.failure(Exception("Error fetching metadata: $responseBody"))
                }
            }

        } catch (e: Exception) {
            Log.e(tag, "Fetch metadata error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun extractSpreadsheetId(spreadsheetUrlOrId: String): String? {
        val raw = spreadsheetUrlOrId.trim()
        if (!raw.contains("docs.google.com/spreadsheets", ignoreCase = true)) return null
        val regex = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)")
        return regex.find(raw)?.groupValues?.getOrNull(1)?.trim().takeIf { !it.isNullOrBlank() }
    }

    private fun normalizeSpreadsheetId(spreadsheetUrlOrId: String): String {
        val clean = spreadsheetUrlOrId.trim()
        return extractSpreadsheetId(clean).orEmpty().ifBlank { clean }
    }

    private fun parseGoogleSheetTokenConfig(rawConfig: String): GoogleSheetTokenConfig? {
        if (rawConfig.isBlank()) return null
        return runCatching {
            val json = JSONObject(rawConfig)
            GoogleSheetTokenConfig(
                clientId = json.optString("clientId").trim(),
                clientSecret = json.optString("clientSecret").trim(),
                accessToken = json.optString("accessToken").trim(),
                refreshToken = json.optString("refreshToken").trim(),
                expiry = json.optLong("expiry", 0L)
            )
        }.getOrNull()
    }

    private fun buildGoogleSheetConfigJson(config: GoogleSheetTokenConfig): String {
        return JSONObject()
            .put("clientId", config.clientId)
            .put("clientSecret", config.clientSecret)
            .put("accessToken", config.accessToken)
            .put("refreshToken", config.refreshToken)
            .put("expiry", config.expiry)
            .toString()
    }

    private suspend fun getValidDirectAccessToken(): Result<String> {
        return try {
            val uid = currentUserId
            if (uid.isBlank()) return Result.failure(Exception("User not logged in."))

            val config = getConfig() ?: return Result.failure(Exception("Google Sheet setup missing."))
            val tokenConfig =
                parseGoogleSheetTokenConfig(config.googleSheetConfig)
                    ?: return Result.failure(Exception("Google Sheet token config missing."))

            val now = System.currentTimeMillis()
            if (tokenConfig.accessToken.isNotBlank() && tokenConfig.expiry > now + 60_000L) {
                return Result.success(tokenConfig.accessToken)
            }
            if (
                tokenConfig.refreshToken.isBlank() ||
                tokenConfig.clientId.isBlank() ||
                tokenConfig.clientSecret.isBlank()
            ) {
                return Result.failure(Exception("Google Sheet token refresh data missing."))
            }

            val refreshBody =
                FormBody.Builder()
                    .add("client_id", tokenConfig.clientId)
                    .add("client_secret", tokenConfig.clientSecret)
                    .add("refresh_token", tokenConfig.refreshToken)
                    .add("grant_type", "refresh_token")
                    .build()

            val refreshRequest =
                Request.Builder()
                    .url("https://oauth2.googleapis.com/token")
                    .post(refreshBody)
                    .build()

            withContext(Dispatchers.IO) {
                httpClient.newCall(refreshRequest).execute()
            }.use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(Exception("Token refresh failed: $responseBody"))
                }

                val json = JSONObject(responseBody)
                val refreshedToken = json.optString("access_token").trim()
                if (refreshedToken.isBlank()) {
                    return Result.failure(Exception("Token refresh returned empty access token."))
                }

                val expiresInSeconds = json.optLong("expires_in", 3600L)
                val updatedConfig =
                    tokenConfig.copy(
                        accessToken = refreshedToken,
                        expiry = System.currentTimeMillis() + (expiresInSeconds * 1000L) - 60_000L
                    )

                firestore.collection("users_config").document(uid)
                    .set(
                        mapOf(
                            "googleSheetConfig" to buildGoogleSheetConfigJson(updatedConfig),
                            "sheetApiConnected" to true,
                            "sheetApiLastSetup" to java.time.Instant.now().toString()
                        ),
                        SetOptions.merge()
                    )
                    .await()

                Result.success(updatedConfig.accessToken)
            }
        } catch (e: Exception) {
            Log.e(tag, "Direct access token error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun buildHeaderRange(sheetTitle: String): String {
        val escapedTitle = sheetTitle.replace("'", "''")
        return "'$escapedTitle'!1:1"
    }

    private fun parseSheetTitleFromRange(range: String): String {
        val rawTitle = range.substringBefore("!").trim()
        if (rawTitle.isBlank()) return ""
        return if (rawTitle.startsWith("'") && rawTitle.endsWith("'") && rawTitle.length >= 2) {
            rawTitle.substring(1, rawTitle.length - 1).replace("''", "'")
        } else {
            rawTitle
        }
    }

    private suspend fun fetchSheetMetadataDirect(spreadsheetUrlOrId: String): Result<SheetMetadataResponse> {
        return try {
            val spreadsheetId = normalizeSpreadsheetId(spreadsheetUrlOrId)
            if (spreadsheetId.isBlank()) {
                return Result.failure(Exception("Invalid Spreadsheet ID/URL"))
            }

            val accessToken =
                getValidDirectAccessToken().getOrElse {
                    return Result.failure(it)
                }

            val metadataRequest =
                Request.Builder()
                    .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId?fields=properties.title,sheets.properties.title")
                    .get()
                    .header("Authorization", "Bearer $accessToken")
                    .build()

            val metadataJson =
                withContext(Dispatchers.IO) {
                    httpClient.newCall(metadataRequest).execute()
                }.use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return Result.failure(Exception("Google metadata fetch failed: $responseBody"))
                    }
                    JSONObject(responseBody)
                }

            val spreadsheetTitle = metadataJson.optJSONObject("properties")?.optString("title").orEmpty().trim()
            val sheetTitles =
                metadataJson.optJSONArray("sheets")
                    ?.let { sheetsArray ->
                        buildList {
                            for (i in 0 until sheetsArray.length()) {
                                val title =
                                    sheetsArray.optJSONObject(i)
                                        ?.optJSONObject("properties")
                                        ?.optString("title")
                                        .orEmpty()
                                        .trim()
                                if (title.isNotBlank()) add(title)
                            }
                        }
                    }
                    .orEmpty()

            val headerMap = mutableMapOf<String, List<String>>()
            if (sheetTitles.isNotEmpty()) {
                val batchUrl =
                    "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchGet"
                        .toHttpUrlOrNull()
                        ?.newBuilder()
                        ?.apply {
                            sheetTitles.forEach { title ->
                                addQueryParameter("ranges", buildHeaderRange(title))
                            }
                            addQueryParameter("majorDimension", "ROWS")
                        }
                        ?.build()

                if (batchUrl != null) {
                    val headerRequest =
                        Request.Builder()
                            .url(batchUrl)
                            .get()
                            .header("Authorization", "Bearer $accessToken")
                            .build()

                    withContext(Dispatchers.IO) {
                        httpClient.newCall(headerRequest).execute()
                    }.use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            val batchJson = JSONObject(responseBody)
                            val valueRanges = batchJson.optJSONArray("valueRanges")
                            if (valueRanges != null) {
                                for (i in 0 until valueRanges.length()) {
                                    val valueRange = valueRanges.optJSONObject(i) ?: continue
                                    val title = parseSheetTitleFromRange(valueRange.optString("range"))
                                    if (title.isBlank()) continue
                                    val firstRow = valueRange.optJSONArray("values")?.optJSONArray(0)
                                    val columns = mutableListOf<String>()
                                    if (firstRow != null) {
                                        for (j in 0 until firstRow.length()) {
                                            val value = firstRow.optString(j).trim()
                                            if (value.isNotBlank()) {
                                                columns.add(value)
                                            }
                                        }
                                    }
                                    headerMap[title] = columns
                                }
                            }
                        } else {
                            Log.w(tag, "Direct header fetch failed: $responseBody")
                        }
                    }
                }
            }

            Result.success(
                SheetMetadataResponse(
                    success = true,
                    spreadsheetId = spreadsheetId,
                    spreadsheetTitle = spreadsheetTitle,
                    sheets = sheetTitles.map { title -> SheetInfo(title, headerMap[title].orEmpty()) },
                    error = null
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "Direct metadata fetch error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Read Sheet Data
     */
    suspend fun readSheetData(spreadsheetUrlOrId: String, range: String): Result<List<List<String>>> {
        return try {
            val uid = currentUserId
            if (uid.isBlank()) return Result.failure(Exception("User not logged in."))

            val body = JSONObject().apply {
                put("userId", uid)
                put("spreadsheetUrlOrId", spreadsheetUrlOrId)
                put("range", range)
            }.toString().toRequestBody(jsonType)

            val request = Request.Builder()
                .url("$workerBaseUrl/sheet/read")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }.use { response ->
                val responseBody = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val valuesArray = json.optJSONArray("values")
                    val results = mutableListOf<List<String>>()

                    if (valuesArray != null) {
                        for (i in 0 until valuesArray.length()) {
                            val rowArray = valuesArray.optJSONArray(i)
                            val rowList = mutableListOf<String>()
                            if (rowArray != null) {
                                for (j in 0 until rowArray.length()) {
                                    rowList.add(rowArray.getString(j))
                                }
                            }
                            results.add(rowList)
                        }
                    }
                    Result.success(results)
                } else {
                    Result.failure(Exception("Error reading sheet: $responseBody"))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Read sheet error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Write/Append Sheet Data
     */
    suspend fun writeSheetData(spreadsheetUrlOrId: String, range: String, values: List<List<Any>>): Result<JSONObject> {
        return try {
            val uid = currentUserId
            if (uid.isBlank()) return Result.failure(Exception("User not logged in."))

            val valuesJsonArray = JSONArray()
            for (row in values) {
                val rowArray = JSONArray()
                for (item in row) {
                    rowArray.put(item)
                }
                valuesJsonArray.put(rowArray)
            }

            val body = JSONObject().apply {
                put("userId", uid)
                put("spreadsheetUrlOrId", spreadsheetUrlOrId)
                put("range", range)
                put("values", valuesJsonArray)
            }.toString().toRequestBody(jsonType)

            val request = Request.Builder()
                .url("$workerBaseUrl/sheet/write")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }.use { response ->
                val responseBody = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    Result.success(JSONObject(responseBody))
                } else {
                    Result.failure(Exception("Error writing sheet: $responseBody"))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Write sheet error: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update Sheet Data over specific range directly
     */
    suspend fun updateSheetData(spreadsheetUrlOrId: String, range: String, values: List<List<Any>>): Result<JSONObject> {
        return try {
            val uid = currentUserId
            if (uid.isBlank()) return Result.failure(Exception("User not logged in."))

            val valuesJsonArray = JSONArray()
            for (row in values) {
                val rowArray = JSONArray()
                for (item in row) {
                    rowArray.put(item)
                }
                valuesJsonArray.put(rowArray)
            }

            val body = JSONObject().apply {
                put("userId", uid)
                put("spreadsheetUrlOrId", spreadsheetUrlOrId)
                put("range", range)
                put("values", valuesJsonArray)
            }.toString().toRequestBody(jsonType)

            val request = Request.Builder()
                .url("$workerBaseUrl/sheet/update")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }.use { response ->
                val responseBody = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    Result.success(JSONObject(responseBody))
                } else {
                    Result.failure(Exception("Error updating sheet: $responseBody"))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Update sheet error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
