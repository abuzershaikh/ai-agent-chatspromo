package com.message.bulksend.autorespond.ai.ecommerce

import android.content.Context
import com.message.bulksend.tablesheet.data.TableSheetDatabase
import com.message.bulksend.tablesheet.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * E-commerce Order Manager
 * Manages order creation and tracking in Sales Orders sheet
 */
class OrderManager(private val context: Context) {
    
    private val database = TableSheetDatabase.getDatabase(context)
    private val folderDao = database.folderDao()
    private val tableDao = database.tableDao()
    private val columnDao = database.columnDao()
    private val rowDao = database.rowDao()
    private val cellDao = database.cellDao()
    
    companion object {
        const val SALES_FOLDER_NAME = "Sales Orders"
        const val ORDERS_SHEET_NAME = "Orders"
        const val PENDING_ORDERS_SHEET_NAME = "Pending Orders"
    }

    /**
     * Initialize Sales Orders folder and sheets
     */
    suspend fun initializeSalesSystem() = withContext(Dispatchers.IO) {
        try {
            // Create folder if not exists
            val folder = ensureSalesFolderExists()
            
            // Create sheets
            ensureOrdersSheetExists(folder.id)
            ensurePendingOrdersSheetExists(folder.id)
            
            android.util.Log.d("OrderManager", "Sales system initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("OrderManager", "Failed to initialize sales system: ${e.message}")
        }
    }

    /**
     * Ensure "Sales Orders" folder exists
     */
    private suspend fun ensureSalesFolderExists(): FolderModel {
        var folder = folderDao.getFolderByName(SALES_FOLDER_NAME)
        
        if (folder == null) {
            val folderId = folderDao.insertFolder(
                FolderModel(
                    name = SALES_FOLDER_NAME,
                    colorHex = "#4CAF50" // Green color for sales
                )
            )
            folder = folderDao.getFolderById(folderId)!!
            android.util.Log.d("OrderManager", "Created sales folder: $SALES_FOLDER_NAME")
        }
        
        return folder
    }

    /**
     * Ensure Orders sheet exists (completed orders)
     */
    private suspend fun ensureOrdersSheetExists(folderId: Long): TableModel {
        val existingTables = tableDao.getTablesByFolderIdSync(folderId)
        var ordersSheet = existingTables.find { it.name == ORDERS_SHEET_NAME }
        
        if (ordersSheet != null) {
            configureOrdersSheet(ordersSheet)
            return ordersSheet
        } else {
            return createPreBuiltOrdersSheet(folderId)
        }
    }
    
    /**
     * Create pre-built orders sheet with 100 rows and 20 columns
     */
    private suspend fun createPreBuiltOrdersSheet(folderId: Long): TableModel {
        val tableId = tableDao.insertTable(
            TableModel(
                name = ORDERS_SHEET_NAME,
                description = "Completed sales orders",
                folderId = folderId,
                columnCount = 20, // 12 required + 8 extra
                rowCount = 100
            )
        )
        
        val columnConfig = listOf(
            Triple("Order ID", ColumnType.STRING, 0),
            Triple("Date", ColumnType.STRING, 1),
            Triple("Customer Name", ColumnType.STRING, 2),
            Triple("Phone Number", ColumnType.PHONE, 3),
            Triple("Product", ColumnType.STRING, 4),
            Triple("Quantity", ColumnType.INTEGER, 5),
            Triple("Price", ColumnType.INTEGER, 6),
            Triple("Total Amount", ColumnType.INTEGER, 7),
            Triple("Address", ColumnType.STRING, 8),
            Triple("Payment Status", ColumnType.SELECT, 9),
            Triple("Delivery Status", ColumnType.SELECT, 10),
            Triple("Notes", ColumnType.STRING, 11)
        )
        
        columnConfig.forEach { (name, type, index) ->
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = name,
                    type = type,
                    orderIndex = index,
                    selectOptions = when (name) {
                        "Payment Status" -> """["PENDING","PAID","REFUNDED"]"""
                        "Delivery Status" -> """["PENDING","SHIPPED","DELIVERED","CANCELLED"]"""
                        else -> null
                    }
                )
            )
        }
        
        // Add 8 extra columns
        repeat(8) { i ->
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = "Column ${i + 13}",
                    type = ColumnType.STRING,
                    orderIndex = 12 + i
                )
            )
        }
        
        // Pre-create 100 empty rows
        repeat(100) { i ->
            rowDao.insertRow(RowModel(tableId = tableId, orderIndex = i))
        }
        
        val ordersSheet = tableDao.getTableById(tableId)!!
        android.util.Log.d("OrderManager", "Pre-created orders sheet: 100 rows x 20 columns")
        return ordersSheet
    }
    
    /**
     * Configure existing orders sheet
     */
    private suspend fun configureOrdersSheet(sheet: TableModel) {
        try {
            val columns = columnDao.getColumnsByTableIdSync(sheet.id)
            val columnConfig = mapOf(
                0 to Triple("Order ID", ColumnType.STRING, null),
                1 to Triple("Date", ColumnType.STRING, null),
                2 to Triple("Customer Name", ColumnType.STRING, null),
                3 to Triple("Phone Number", ColumnType.PHONE, null),
                4 to Triple("Product", ColumnType.STRING, null),
                5 to Triple("Quantity", ColumnType.INTEGER, null),
                6 to Triple("Price", ColumnType.INTEGER, null),
                7 to Triple("Total Amount", ColumnType.INTEGER, null),
                8 to Triple("Address", ColumnType.STRING, null),
                9 to Triple("Payment Status", ColumnType.SELECT, """["PENDING","PAID","REFUNDED"]"""),
                10 to Triple("Delivery Status", ColumnType.SELECT, """["PENDING","SHIPPED","DELIVERED","CANCELLED"]"""),
                11 to Triple("Notes", ColumnType.STRING, null)
            )
            
            columnConfig.forEach { (index, config) ->
                if (index < columns.size) {
                    val column = columns[index]
                    columnDao.updateColumn(
                        column.copy(
                            name = config.first,
                            type = config.second,
                            selectOptions = config.third
                        )
                    )
                }
            }
            
            android.util.Log.d("OrderManager", "Configured orders sheet columns")
        } catch (e: Exception) {
            android.util.Log.e("OrderManager", "Failed to configure orders sheet: ${e.message}")
        }
    }

    /**
     * Ensure Pending Orders sheet exists (orders awaiting address)
     */
    private suspend fun ensurePendingOrdersSheetExists(folderId: Long): TableModel {
        val existingTables = tableDao.getTablesByFolderIdSync(folderId)
        var pendingSheet = existingTables.find { it.name == PENDING_ORDERS_SHEET_NAME }
        
        if (pendingSheet != null) {
            configurePendingOrdersSheet(pendingSheet)
            return pendingSheet
        } else {
            return createPreBuiltPendingOrdersSheet(folderId)
        }
    }
    
    /**
     * Create pre-built pending orders sheet
     */
    private suspend fun createPreBuiltPendingOrdersSheet(folderId: Long): TableModel {
        val tableId = tableDao.insertTable(
            TableModel(
                name = PENDING_ORDERS_SHEET_NAME,
                description = "Orders pending address confirmation",
                folderId = folderId,
                columnCount = 15,
                rowCount = 50
            )
        )
        
        val columnConfig = listOf(
            Triple("Phone Number", ColumnType.PHONE, 0),
            Triple("Customer Name", ColumnType.STRING, 1),
            Triple("Product", ColumnType.STRING, 2),
            Triple("Quantity", ColumnType.INTEGER, 3),
            Triple("Price", ColumnType.INTEGER, 4),
            Triple("Total Amount", ColumnType.INTEGER, 5),
            Triple("Status", ColumnType.SELECT, 6),
            Triple("Created At", ColumnType.STRING, 7)
        )
        
        columnConfig.forEach { (name, type, index) ->
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = name,
                    type = type,
                    orderIndex = index,
                    selectOptions = if (name == "Status") """["AWAITING_ADDRESS","COMPLETED","CANCELLED"]""" else null
                )
            )
        }
        
        // Add 7 extra columns
        repeat(7) { i ->
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = "Column ${i + 9}",
                    type = ColumnType.STRING,
                    orderIndex = 8 + i
                )
            )
        }
        
        // Pre-create 50 empty rows
        repeat(50) { i ->
            rowDao.insertRow(RowModel(tableId = tableId, orderIndex = i))
        }
        
        val pendingSheet = tableDao.getTableById(tableId)!!
        android.util.Log.d("OrderManager", "Pre-created pending orders sheet: 50 rows x 15 columns")
        return pendingSheet
    }
    
    /**
     * Configure existing pending orders sheet
     */
    private suspend fun configurePendingOrdersSheet(sheet: TableModel) {
        try {
            val columns = columnDao.getColumnsByTableIdSync(sheet.id)
            val columnConfig = mapOf(
                0 to Triple("Phone Number", ColumnType.PHONE, null),
                1 to Triple("Customer Name", ColumnType.STRING, null),
                2 to Triple("Product", ColumnType.STRING, null),
                3 to Triple("Quantity", ColumnType.INTEGER, null),
                4 to Triple("Price", ColumnType.INTEGER, null),
                5 to Triple("Total Amount", ColumnType.INTEGER, null),
                6 to Triple("Status", ColumnType.SELECT, """["AWAITING_ADDRESS","COMPLETED","CANCELLED"]"""),
                7 to Triple("Created At", ColumnType.STRING, null)
            )
            
            columnConfig.forEach { (index, config) ->
                if (index < columns.size) {
                    val column = columns[index]
                    columnDao.updateColumn(
                        column.copy(
                            name = config.first,
                            type = config.second,
                            selectOptions = config.third
                        )
                    )
                }
            }
            
            android.util.Log.d("OrderManager", "Configured pending orders sheet columns")
        } catch (e: Exception) {
            android.util.Log.e("OrderManager", "Failed to configure pending orders sheet: ${e.message}")
        }
    }

    /**
     * Create pending order (after payment, before address)
     */
    suspend fun createPendingOrder(
        phoneNumber: String,
        customerName: String,
        product: String,
        quantity: Int,
        price: Int
    ): Long = withContext(Dispatchers.IO) {
        try {
            val folder = folderDao.getFolderByName(SALES_FOLDER_NAME) ?: return@withContext -1L
            val sheet = ensurePendingOrdersSheetExists(folder.id)
            val columns = columnDao.getColumnsByTableIdSync(sheet.id)
            
            // Find first empty row
            val allRows = rowDao.getRowsByTableIdSync(sheet.id)
            var emptyRow: RowModel? = null
            
            for (row in allRows) {
                val cells = cellDao.getCellsByRowIdSync(row.id)
                if (cells.isEmpty() || cells.all { it.value.isBlank() }) {
                    emptyRow = row
                    break
                }
            }
            
            // If no empty row, expand
            if (emptyRow == null) {
                val maxOrderIndex = rowDao.getMaxOrderIndex(sheet.id) ?: -1
                repeat(25) { i ->
                    rowDao.insertRow(RowModel(tableId = sheet.id, orderIndex = maxOrderIndex + i + 1))
                }
                emptyRow = rowDao.getRowsByTableIdSync(sheet.id).first { row ->
                    val cells = cellDao.getCellsByRowIdSync(row.id)
                    cells.isEmpty() || cells.all { it.value.isBlank() }
                }
            }
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val totalAmount = quantity * price
            
            val cellData = mapOf(
                "Phone Number" to phoneNumber,
                "Customer Name" to customerName,
                "Product" to product,
                "Quantity" to quantity.toString(),
                "Price" to price.toString(),
                "Total Amount" to totalAmount.toString(),
                "Status" to "AWAITING_ADDRESS",
                "Created At" to timestamp
            )
            
            cellData.forEach { (columnName, value) ->
                val column = columns.find { it.name == columnName }
                if (column != null) {
                    cellDao.insertCell(
                        CellModel(
                            rowId = emptyRow!!.id,
                            columnId = column.id,
                            value = value
                        )
                    )
                }
            }
            
            android.util.Log.d("OrderManager", "Created pending order for $phoneNumber")
            return@withContext emptyRow!!.id
            
        } catch (e: Exception) {
            android.util.Log.e("OrderManager", "Failed to create pending order: ${e.message}")
            return@withContext -1L
        }
    }

    /**
     * Complete order (move from pending to orders with address)
     */
    suspend fun completeOrder(
        phoneNumber: String,
        address: String,
        notes: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val folder = folderDao.getFolderByName(SALES_FOLDER_NAME) ?: return@withContext false
            
            // Find pending order
            val pendingSheet = ensurePendingOrdersSheetExists(folder.id)
            val pendingColumns = columnDao.getColumnsByTableIdSync(pendingSheet.id)
            val phoneColumn = pendingColumns.find { it.name == "Phone Number" }
            
            if (phoneColumn == null) return@withContext false
            
            val pendingCells = cellDao.findCellsByColumnAndValue(phoneColumn.id, phoneNumber)
            if (pendingCells.isEmpty()) return@withContext false
            
            val pendingRowId = pendingCells.first().rowId
            val pendingCellsData = cellDao.getCellsByRowIdSync(pendingRowId)
            
            // Extract order data
            val orderData = mutableMapOf<String, String>()
            pendingCellsData.forEach { cell ->
                val column = pendingColumns.find { it.id == cell.columnId }
                if (column != null) {
                    orderData[column.name] = cell.value
                }
            }
            
            // Create completed order
            val ordersSheet = ensureOrdersSheetExists(folder.id)
            val ordersColumns = columnDao.getColumnsByTableIdSync(ordersSheet.id)
            
            // Find first empty row in orders sheet
            val allRows = rowDao.getRowsByTableIdSync(ordersSheet.id)
            var emptyRow: RowModel? = null
            
            for (row in allRows) {
                val cells = cellDao.getCellsByRowIdSync(row.id)
                if (cells.isEmpty() || cells.all { it.value.isBlank() }) {
                    emptyRow = row
                    break
                }
            }
            
            // If no empty row, expand
            if (emptyRow == null) {
                val maxOrderIndex = rowDao.getMaxOrderIndex(ordersSheet.id) ?: -1
                repeat(50) { i ->
                    rowDao.insertRow(RowModel(tableId = ordersSheet.id, orderIndex = maxOrderIndex + i + 1))
                }
                emptyRow = rowDao.getRowsByTableIdSync(ordersSheet.id).first { row ->
                    val cells = cellDao.getCellsByRowIdSync(row.id)
                    cells.isEmpty() || cells.all { it.value.isBlank() }
                }
            }
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val orderId = "ORD${System.currentTimeMillis()}"
            
            val completedOrderData = mapOf(
                "Order ID" to orderId,
                "Date" to timestamp,
                "Customer Name" to (orderData["Customer Name"] ?: "Unknown"),
                "Phone Number" to phoneNumber,
                "Product" to (orderData["Product"] ?: ""),
                "Quantity" to (orderData["Quantity"] ?: "1"),
                "Price" to (orderData["Price"] ?: "0"),
                "Total Amount" to (orderData["Total Amount"] ?: "0"),
                "Address" to address,
                "Payment Status" to "PAID",
                "Delivery Status" to "PENDING",
                "Notes" to notes
            )
            
            completedOrderData.forEach { (columnName, value) ->
                val column = ordersColumns.find { it.name == columnName }
                if (column != null) {
                    cellDao.insertCell(
                        CellModel(
                            rowId = emptyRow!!.id,
                            columnId = column.id,
                            value = value
                        )
                    )
                }
            }
            
            // Update pending order status to COMPLETED
            val statusColumn = pendingColumns.find { it.name == "Status" }
            if (statusColumn != null) {
                val statusCell = cellDao.getCellSync(pendingRowId, statusColumn.id)
                if (statusCell != null) {
                    cellDao.updateCell(statusCell.copy(value = "COMPLETED"))
                }
            }
            
            android.util.Log.d("OrderManager", "Completed order $orderId for $phoneNumber")
            return@withContext true
            
        } catch (e: Exception) {
            android.util.Log.e("OrderManager", "Failed to complete order: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Check if user has pending order
     */
    suspend fun hasPendingOrder(phoneNumber: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val folder = folderDao.getFolderByName(SALES_FOLDER_NAME) ?: return@withContext false
            val pendingSheet = ensurePendingOrdersSheetExists(folder.id)
            val columns = columnDao.getColumnsByTableIdSync(pendingSheet.id)
            
            val phoneColumn = columns.find { it.name == "Phone Number" }
            if (phoneColumn == null) return@withContext false
            
            val cells = cellDao.findCellsByColumnAndValue(phoneColumn.id, phoneNumber)
            if (cells.isEmpty()) return@withContext false
            
            // Check if status is AWAITING_ADDRESS
            val statusColumn = columns.find { it.name == "Status" }
            if (statusColumn != null) {
                val statusCell = cellDao.getCellSync(cells.first().rowId, statusColumn.id)
                return@withContext statusCell?.value == "AWAITING_ADDRESS"
            }
            
            return@withContext false
        } catch (e: Exception) {
            android.util.Log.e("OrderManager", "Failed to check pending order: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Get pending order details
     */
    suspend fun getPendingOrderDetails(phoneNumber: String): Map<String, String>? = withContext(Dispatchers.IO) {
        try {
            val folder = folderDao.getFolderByName(SALES_FOLDER_NAME) ?: return@withContext null
            val pendingSheet = ensurePendingOrdersSheetExists(folder.id)
            val columns = columnDao.getColumnsByTableIdSync(pendingSheet.id)
            
            val phoneColumn = columns.find { it.name == "Phone Number" }
            if (phoneColumn == null) return@withContext null
            
            val cells = cellDao.findCellsByColumnAndValue(phoneColumn.id, phoneNumber)
            if (cells.isEmpty()) return@withContext null
            
            val rowId = cells.first().rowId
            val rowCells = cellDao.getCellsByRowIdSync(rowId)
            
            val orderData = mutableMapOf<String, String>()
            rowCells.forEach { cell ->
                val column = columns.find { it.id == cell.columnId }
                if (column != null) {
                    orderData[column.name] = cell.value
                }
            }
            
            return@withContext orderData
        } catch (e: Exception) {
            android.util.Log.e("OrderManager", "Failed to get pending order: ${e.message}")
            return@withContext null
        }
    }
}
