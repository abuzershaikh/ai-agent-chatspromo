package com.message.bulksend.tablesheet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.message.bulksend.userdetails.UserDetailsPreferences
import com.message.bulksend.tablesheet.data.models.FolderModel
import com.message.bulksend.tablesheet.data.TableSheetDatabase
import com.message.bulksend.tablesheet.data.models.ColumnType
import com.message.bulksend.tablesheet.data.models.TableModel
import com.message.bulksend.tablesheet.data.repository.TableSheetRepository
import com.message.bulksend.tablesheet.ui.CreateTableDialog
import com.message.bulksend.tablesheet.ui.FolderDetailScreen
import com.message.bulksend.tablesheet.ui.ImportLinkDialog
import com.message.bulksend.tablesheet.ui.ImportPreviewScreen
import com.message.bulksend.tablesheet.ui.TableListScreen
import com.message.bulksend.tablesheet.utils.FileImporter
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TableSheetActivity : ComponentActivity() {
    
    private lateinit var repository: TableSheetRepository
    private var importedData by mutableStateOf<FileImporter.ImportedData?>(null)
    private var showImportLinkDialog by mutableStateOf(false)
    private var currentFolderId by mutableStateOf<Long?>(null) // Track current folder context
    
    // File picker launcher
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleFileImport(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = TableSheetDatabase.getDatabase(this)
        repository = TableSheetRepository(
            database.tableDao(),
            database.columnDao(),
            database.rowDao(),
            database.cellDao(),
            database.folderDao()
        )

        // Ensure "AI Agent Data Sheet" folder exists (Pre-build)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                repository.createFolderIfNotExists("AI Agent Data Sheet")
                
                // NEW: Initialize AI Agent History System
                val historyManager = com.message.bulksend.autorespond.ai.history.AIAgentHistoryManager(this@TableSheetActivity)
                historyManager.initializeHistorySystem()
                
                // NEW: Initialize E-commerce Order System
                val orderManager = com.message.bulksend.autorespond.ai.ecommerce.OrderManager(this@TableSheetActivity)
                orderManager.initializeSalesSystem()

                // NEW: Initialize Payment Sheets (Razorpay / QR / General)
                val paymentSheetManager = com.message.bulksend.autorespond.ai.payment.PaymentSheetManager(this@TableSheetActivity)
                paymentSheetManager.initializePaymentSystem()

                // NEW: Initialize AgentForm sheets
                com.message.bulksend.aiagent.tools.agentform.AgentFormPredefinedTemplates.seedIfNeeded(this@TableSheetActivity)
                val agentFormSheetManager = com.message.bulksend.aiagent.tools.agentform.AgentFormTableSheetSyncManager(this@TableSheetActivity)
                agentFormSheetManager.initializeAgentFormSheetSystem()
                syncAgentFormResponsesFromCloud(agentFormSheetManager)
                
                android.util.Log.d("TableSheetActivity", "AI Agent folders initialized")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Check if activity was opened with a file (from file manager or other apps)
        handleIncomingIntent(intent)
        
        // Check if we need to open a specific folder
        val openFolderName = intent.getStringExtra("openFolder")
        
        setContent {
            BulksendTestTheme {
                // Show import link dialog
                if (showImportLinkDialog) {
                    ImportLinkDialog(
                        onDismiss = { showImportLinkDialog = false },
                        onImport = { url ->
                            handleUrlImport(url)
                        }
                    )
                }
                
                if (importedData != null) {
                    ImportPreviewScreen(
                        importedData = importedData!!,
                        onConfirm = { tableName ->
                            createTableFromImport(tableName, importedData!!, currentFolderId)
                        },
                        onCancel = { 
                            importedData = null
                            currentFolderId = null
                        }
                    )
                } else {
                    TableSheetScreen(
                        repository = repository,
                        onBackPressed = { finish() },
                        onOpenTable = { table ->
                            val intent = Intent(this, TableEditorActivity::class.java)
                            intent.putExtra("tableId", table.id)
                            intent.putExtra("tableName", table.name)
                            startActivity(intent)
                        },
                        onImportFile = { folderId ->
                            currentFolderId = folderId
                            filePickerLauncher.launch("*/*")
                        },
                        onImportFromLink = { folderId ->
                            currentFolderId = folderId
                            showImportLinkDialog = true
                        },
                        context = this,
                        initialFolderName = openFolderName
                    )
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }
    
    /**
     * Handle incoming file from external apps (file manager, downloads, etc.)
     */
    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                // Show a toast to inform user
                Toast.makeText(
                    this,
                    "Opening file in TableSheet...",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Import the file
                handleFileImport(uri)
            }
        }
    }
    
    private fun handleFileImport(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val data = FileImporter.importFile(this@TableSheetActivity, uri)
                withContext(Dispatchers.Main) {
                    if (data != null) {
                        importedData = data
                    } else {
                        Toast.makeText(
                            this@TableSheetActivity,
                            "Failed to import file. Please check file format.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@TableSheetActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun handleUrlImport(url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@TableSheetActivity,
                        "Fetching data from link...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                val data = FileImporter.importFromUrl(url)
                
                withContext(Dispatchers.Main) {
                    showImportLinkDialog = false
                    
                    if (data != null) {
                        importedData = data
                        Toast.makeText(
                            this@TableSheetActivity,
                            "Data fetched successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@TableSheetActivity,
                            "Failed to fetch data. Make sure the sheet is publicly accessible.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showImportLinkDialog = false
                    Toast.makeText(
                        this@TableSheetActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun createTableFromImport(tableName: String, data: FileImporter.ImportedData, folderId: Long?) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Create table with columns and rows in the specified folder
                val tableId = repository.createTableFromImport(
                    name = tableName,
                    description = "Imported from ${data.fileName}",
                    headers = data.headers,
                    rows = data.rows,
                    folderId = folderId
                )
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@TableSheetActivity,
                        "Table imported successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    importedData = null
                    currentFolderId = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@TableSheetActivity,
                        "Error creating table: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private suspend fun syncAgentFormResponsesFromCloud(
        agentFormSheetManager: com.message.bulksend.aiagent.tools.agentform.AgentFormTableSheetSyncManager
    ) {
        try {
            val ownerUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty().trim()
            if (ownerUid.isBlank()) return

            val prefs = UserDetailsPreferences(this@TableSheetActivity)
            var ownerPhone = sanitizePhone(prefs.getPhoneNumber())
            if (ownerPhone.isBlank()) {
                val fetchedPhone =
                    FirebaseFirestore.getInstance()
                        .collection("userDetails")
                        .document(ownerUid)
                        .get()
                        .await()
                        .getString("phoneNumber")
                ownerPhone = sanitizePhone(fetchedPhone)
                if (ownerPhone.isNotBlank()) {
                    prefs.updatePhoneNumber(ownerPhone)
                }
            }
            if (ownerPhone.isBlank()) return

            val snapshot =
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(ownerUid)
                    .collection("numbers")
                    .document(ownerPhone)
                    .collection("responses")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(200)
                    .get()
                    .await()

            val updatedRows =
                agentFormSheetManager.syncResponseDocuments(
                    ownerUid = ownerUid,
                    ownerPhone = ownerPhone,
                    documents = snapshot.documents
                )
            Log.d("TableSheetActivity", "AgentForm cloud sync rows updated: $updatedRows")
        } catch (e: Exception) {
            Log.e("TableSheetActivity", "Failed to sync AgentForm responses from cloud: ${e.message}", e)
        }
    }

    private fun sanitizePhone(value: String?): String {
        return value.orEmpty().replace(Regex("[^0-9]"), "")
    }

}

@Composable
fun TableSheetScreen(
    repository: TableSheetRepository,
    onBackPressed: () -> Unit,
    onOpenTable: (TableModel) -> Unit,
    onImportFile: (Long?) -> Unit,
    onImportFromLink: (Long?) -> Unit,
    context: android.content.Context,
    initialFolderName: String? = null
) {
    val tables by repository.getAllTables().collectAsState(initial = emptyList())
    val folders by repository.getAllFolders().collectAsState(initial = emptyList())
    var folderTableCounts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var loadingTableId by remember { mutableStateOf<Long?>(null) }
    var selectedFolder by remember { mutableStateOf<FolderModel?>(null) }
    val scope = rememberCoroutineScope()
    val agentFormSheetManager = remember(context) {
        com.message.bulksend.aiagent.tools.agentform.AgentFormTableSheetSyncManager(context)
    }
    
    // Auto-open folder if initialFolderName is provided
    LaunchedEffect(initialFolderName, folders) {
        if (initialFolderName != null && selectedFolder == null && folders.isNotEmpty()) {
            val targetFolder = folders.find { 
                it.name.equals(initialFolderName, ignoreCase = true) 
            }
            if (targetFolder != null) {
                selectedFolder = targetFolder
            }
        }
    }
    
    // Update folder table counts
    LaunchedEffect(folders, tables) {
        scope.launch {
            val counts = mutableMapOf<Long, Int>()
            folders.forEach { folder ->
                counts[folder.id] = tables.count { it.folderId == folder.id }
            }
            folderTableCounts = counts
        }
    }
    
    if (selectedFolder != null) {
        // Show folder detail screen
        FolderDetailScreen(
            folder = selectedFolder!!,
            tables = tables.filter { it.folderId == selectedFolder!!.id },
            onBackPressed = { selectedFolder = null },
            onTableClick = { table ->
                loadingTableId = table.id
                scope.launch {
                    kotlinx.coroutines.delay(300)
                    loadingTableId = null
                    onOpenTable(table)
                }
            },
            onCreateTable = { showCreateDialog = true },
            onImportFile = { onImportFile(selectedFolder!!.id) },
            onImportFromLink = { onImportFromLink(selectedFolder!!.id) },
            onDeleteTable = { table ->
                scope.launch {
                    runCatching {
                        agentFormSheetManager.deleteTemplateSheetDataEverywhere(table)
                    }.onFailure { error ->
                        Log.e("TableSheetActivity", "AgentForm sheet delete sync failed: ${error.message}", error)
                    }
                    repository.deleteTable(table.id)
                }
            },
            onRenameTable = { table, newName ->
                scope.launch {
                    repository.updateTable(table.copy(name = newName))
                }
            },
            onMoveTableOut = { table ->
                scope.launch {
                    repository.moveTableToFolder(table.id, null)
                }
            },
            loadingTableId = loadingTableId
        )
    } else {
        // Show main table list screen
        TableListScreen(
            tables = tables.filter { it.folderId == null }, // Only show tables not in folders
            folders = folders,
            folderTableCounts = folderTableCounts,
            onTableClick = { table ->
                // Show loading and preload data
                loadingTableId = table.id
                scope.launch {
                    // Preload data in background
                    kotlinx.coroutines.delay(300) // Small delay for UI feedback
                    loadingTableId = null
                    onOpenTable(table)
                }
            },
            onCreateTable = { showCreateDialog = true },
            onImportFile = { onImportFile(null) },
            onImportFromLink = { onImportFromLink(null) },
            onDeleteTable = { table ->
                scope.launch {
                    runCatching {
                        agentFormSheetManager.deleteTemplateSheetDataEverywhere(table)
                    }.onFailure { error ->
                        Log.e("TableSheetActivity", "AgentForm sheet delete sync failed: ${error.message}", error)
                    }
                    repository.deleteTable(table.id)
                }
            },
            onRenameTable = { table, newName ->
                scope.launch {
                    repository.updateTable(table.copy(name = newName))
                }
            },
            onMoveToFolder = { table, folderId ->
                scope.launch {
                    repository.moveTableToFolder(table.id, folderId)
                }
            },
            onCreateFolder = { name ->
                scope.launch {
                    repository.createFolder(name)
                }
            },
            onDeleteFolder = { folder ->
                scope.launch {
                    repository.deleteFolder(folder)
                }
            },
            onRenameFolder = { folder, newName ->
                scope.launch {
                    repository.updateFolder(folder.copy(name = newName))
                }
            },
            onFolderClick = { folder ->
                selectedFolder = folder
            },
            onBackPressed = onBackPressed,
            loadingTableId = loadingTableId,
            onRefreshSync = {
                // Refresh functionality can be added here if needed
            }
        )
    }
    
    if (showCreateDialog) {
        CreateTableDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description, tags ->
                scope.launch {
                    val tableId = repository.createTable(
                        name = name,
                        description = description,
                        tags = if (tags.isBlank()) null else tags,
                        folderId = selectedFolder?.id
                    )
                    showCreateDialog = false
                }
            }
        )
    }
}
