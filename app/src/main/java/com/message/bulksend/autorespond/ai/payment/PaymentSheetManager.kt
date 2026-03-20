package com.message.bulksend.autorespond.ai.payment

import android.content.Context
import android.util.Log
import com.message.bulksend.aiagent.tools.ecommerce.PaymentMethodManager
import com.message.bulksend.aiagent.tools.ecommerce.PaymentMethodType
import com.message.bulksend.aiagent.tools.ecommerce.RazorPaymentManager
import com.message.bulksend.aiagent.tools.paymentverification.PaymentVerification
import com.message.bulksend.tablesheet.data.TableSheetDatabase
import com.message.bulksend.tablesheet.data.models.CellModel
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.ColumnType
import com.message.bulksend.tablesheet.data.models.FolderModel
import com.message.bulksend.tablesheet.data.models.RowModel
import com.message.bulksend.tablesheet.data.models.TableModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Stores confirmed / checked payment records in dedicated TableSheet sheets.
 *
 * Folder: Payment
 * - Razorpay Payments
 * - QR Payments
 * - General Payments
 */
class PaymentSheetManager(context: Context) {

    private val database = TableSheetDatabase.getDatabase(context.applicationContext)
    private val folderDao = database.folderDao()
    private val tableDao = database.tableDao()
    private val columnDao = database.columnDao()
    private val rowDao = database.rowDao()
    private val cellDao = database.cellDao()
    private val paymentMethodManager = PaymentMethodManager(context.applicationContext)

    companion object {
        private const val TAG = "PaymentSheetManager"

        const val PAYMENT_FOLDER_NAME = "Payment"
        const val RAZORPAY_SHEET_NAME = "Razorpay Payments"
        const val QR_SHEET_NAME = "QR Payments"
        const val GENERAL_SHEET_NAME = "General Payments"

        private const val PREBUILT_ROWS = 120
        private const val EXPAND_ROWS = 25
        private const val MIN_BUFFER_ROWS = 40
    }

    private enum class TargetSheet {
        RAZORPAY,
        QR,
        GENERAL
    }

    private data class SheetSpec(
        val name: String,
        val description: String,
        val source: String
    )

    private data class ColumnSpec(
        val name: String,
        val type: String,
        val selectOptions: String? = null
    )

    private val sheetSpecs =
        mapOf(
            TargetSheet.RAZORPAY to
                SheetSpec(
                    name = RAZORPAY_SHEET_NAME,
                    description = "Razorpay payment status records",
                    source = "RAZORPAY"
                ),
            TargetSheet.QR to
                SheetSpec(
                    name = QR_SHEET_NAME,
                    description = "QR payment verification records",
                    source = "QR"
                ),
            TargetSheet.GENERAL to
                SheetSpec(
                    name = GENERAL_SHEET_NAME,
                    description = "UPI/Bank/other payment verification records",
                    source = "GENERAL"
                )
        )

    private val columnSpecs =
        listOf(
            ColumnSpec("Record ID", ColumnType.STRING),
            ColumnSpec("Created At", ColumnType.STRING),
            ColumnSpec("Updated At", ColumnType.STRING),
            ColumnSpec("Phone Number", ColumnType.PHONE),
            ColumnSpec("Customer Name", ColumnType.STRING),
            ColumnSpec(
                "Source",
                ColumnType.SELECT,
                """["RAZORPAY","QR","GENERAL"]"""
            ),
            ColumnSpec(
                "Status",
                ColumnType.SELECT,
                """["PENDING","PAID","APPROVED","MANUAL_REVIEW","REJECTED","EXPIRED","CANCELLED","UNKNOWN"]"""
            ),
            ColumnSpec("Amount", ColumnType.AMOUNT),
            ColumnSpec("Transaction ID", ColumnType.STRING),
            ColumnSpec("Link ID", ColumnType.STRING),
            ColumnSpec("Order ID", ColumnType.STRING),
            ColumnSpec("UPI ID", ColumnType.STRING),
            ColumnSpec("Account/Bank Details", ColumnType.STRING),
            ColumnSpec("Payment Time", ColumnType.STRING),
            ColumnSpec("Description", ColumnType.STRING),
            ColumnSpec("Hints", ColumnType.STRING)
        )

    suspend fun initializePaymentSystem() = withContext(Dispatchers.IO) {
        try {
            val folder = ensurePaymentFolderExists()
            sheetSpecs.values.forEach { spec ->
                ensurePaymentSheetExists(folder.id, spec)
            }
            Log.d(TAG, "Payment TableSheet system initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize payment sheet system: ${e.message}", e)
        }
    }

    suspend fun logRazorpayStatus(
        phoneNumber: String,
        fallbackCustomerName: String,
        link: RazorPaymentManager.PaymentLinkInfo,
        status: String
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val folder = ensurePaymentFolderExists()
                val sheet = ensurePaymentSheetExists(folder.id, sheetSpecs.getValue(TargetSheet.RAZORPAY))
                val now = nowFormatted()
                val normalizedStatus = normalizeStatus(status.ifBlank { link.status })
                val paymentTime = formatCreatedAt(link.createdAt).ifBlank { link.createdAt }
                val customerName =
                    link.customerName?.takeIf { it.isNotBlank() } ?: fallbackCustomerName
                val amountText = if (link.amount > 0) formatAmount(link.amount) else ""

                val data =
                    linkedMapOf(
                        "Record ID" to "RZP:${link.id}",
                        "Created At" to now,
                        "Updated At" to now,
                        "Phone Number" to phoneNumber,
                        "Customer Name" to customerName,
                        "Source" to sheetSpecs.getValue(TargetSheet.RAZORPAY).source,
                        "Status" to normalizedStatus,
                        "Amount" to amountText,
                        "Transaction ID" to "",
                        "Link ID" to link.id,
                        "Order ID" to "",
                        "UPI ID" to "",
                        "Account/Bank Details" to "",
                        "Payment Time" to paymentTime,
                        "Description" to link.description,
                        "Hints" to buildRazorpayHints(phoneNumber, link, customerName, amountText)
                    )

                upsertRecord(sheet, data)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log Razorpay payment in sheet: ${e.message}", e)
                false
            }
        }

    suspend fun logScreenshotVerification(
        phoneNumber: String,
        fallbackCustomerName: String,
        verification: PaymentVerification
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTargetSheet(verification)
                val spec = sheetSpecs.getValue(target)
                val folder = ensurePaymentFolderExists()
                val sheet = ensurePaymentSheetExists(folder.id, spec)
                val now = nowFormatted()
                val amountValue =
                    when {
                        verification.amount > 0 -> verification.amount
                        verification.expectedAmount > 0 -> verification.expectedAmount
                        else -> 0.0
                    }
                val amountText = if (amountValue > 0) formatAmount(amountValue) else ""

                val customerName =
                    verification.expectedName.takeIf { it.isNotBlank() }
                        ?: verification.payeeName.takeIf { it.isNotBlank() }
                        ?: verification.payerName.takeIf { it.isNotBlank() }
                        ?: fallbackCustomerName

                val statusInput =
                    when {
                        verification.status.isNotBlank() &&
                            !verification.status.equals("PENDING", ignoreCase = true) ->
                            verification.status
                        verification.recommendation.isNotBlank() -> verification.recommendation
                        verification.paymentStatus.isNotBlank() -> verification.paymentStatus
                        else -> verification.status
                    }

                val data =
                    linkedMapOf(
                        "Record ID" to "PV:${verification.id}",
                        "Created At" to now,
                        "Updated At" to now,
                        "Phone Number" to phoneNumber,
                        "Customer Name" to customerName,
                        "Source" to spec.source,
                        "Status" to normalizeStatus(statusInput),
                        "Amount" to amountText,
                        "Transaction ID" to verification.transactionId,
                        "Link ID" to "",
                        "Order ID" to verification.orderId,
                        "UPI ID" to (verification.upiId.ifBlank { verification.expectedUpiId }),
                        "Account/Bank Details" to buildVerificationAccountDetails(verification),
                        "Payment Time" to buildVerificationPaymentTime(verification),
                        "Description" to buildVerificationDescription(verification),
                        "Hints" to buildVerificationHints(phoneNumber, verification, amountText)
                    )

                upsertRecord(sheet, data)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log screenshot verification in sheet: ${e.message}", e)
                false
            }
        }

    private suspend fun ensurePaymentFolderExists(): FolderModel {
        var folder = folderDao.getFolderByName(PAYMENT_FOLDER_NAME)
        if (folder == null) {
            val folderId =
                folderDao.insertFolder(
                    FolderModel(
                        name = PAYMENT_FOLDER_NAME,
                        colorHex = "#16A34A"
                    )
                )
            folder = folderDao.getFolderById(folderId)
        }
        return requireNotNull(folder)
    }

    private suspend fun ensurePaymentSheetExists(folderId: Long, spec: SheetSpec): TableModel {
        val existing = tableDao.getTablesByFolderIdSync(folderId).find { it.name == spec.name }
        return if (existing != null) {
            configurePaymentSheet(existing)
            ensureMinimumRows(existing.id)
            existing
        } else {
            createPreBuiltPaymentSheet(folderId, spec)
        }
    }

    private suspend fun createPreBuiltPaymentSheet(folderId: Long, spec: SheetSpec): TableModel {
        val totalColumns = columnSpecs.size + 4
        val tableId =
            tableDao.insertTable(
                TableModel(
                    name = spec.name,
                    description = spec.description,
                    folderId = folderId,
                    columnCount = totalColumns,
                    rowCount = PREBUILT_ROWS
                )
            )

        columnSpecs.forEachIndexed { index, column ->
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = column.name,
                    type = column.type,
                    orderIndex = index,
                    selectOptions = column.selectOptions
                )
            )
        }

        repeat(4) { i ->
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = "Column ${columnSpecs.size + i + 1}",
                    type = ColumnType.STRING,
                    orderIndex = columnSpecs.size + i
                )
            )
        }

        repeat(PREBUILT_ROWS) { i ->
            rowDao.insertRow(RowModel(tableId = tableId, orderIndex = i))
        }

        return requireNotNull(tableDao.getTableById(tableId))
    }

    private suspend fun configurePaymentSheet(sheet: TableModel) {
        val existingColumns = columnDao.getColumnsByTableIdSync(sheet.id).toMutableList()

        if (existingColumns.size < columnSpecs.size) {
            for (index in existingColumns.size until columnSpecs.size) {
                val column = columnSpecs[index]
                columnDao.insertColumn(
                    ColumnModel(
                        tableId = sheet.id,
                        name = column.name,
                        type = column.type,
                        orderIndex = index,
                        selectOptions = column.selectOptions
                    )
                )
            }
        }

        val freshColumns = columnDao.getColumnsByTableIdSync(sheet.id)
        columnSpecs.forEachIndexed { index, spec ->
            if (index < freshColumns.size) {
                val column = freshColumns[index]
                columnDao.updateColumn(
                    column.copy(
                        name = spec.name,
                        type = spec.type,
                        selectOptions = spec.selectOptions
                    )
                )
            }
        }

        val finalColumnCount = columnDao.getColumnsByTableIdSync(sheet.id).size
        tableDao.updateColumnCount(sheet.id, finalColumnCount)
    }

    private suspend fun ensureMinimumRows(tableId: Long) {
        val rows = rowDao.getRowsByTableIdSync(tableId)
        if (rows.size >= MIN_BUFFER_ROWS) return

        val maxOrderIndex = rowDao.getMaxOrderIndex(tableId) ?: -1
        val toAdd = (MIN_BUFFER_ROWS - rows.size).coerceAtLeast(0)
        repeat(toAdd) { i ->
            rowDao.insertRow(RowModel(tableId = tableId, orderIndex = maxOrderIndex + i + 1))
        }
        tableDao.updateRowCount(tableId, rowDao.getRowCountSync(tableId))
    }

    private suspend fun upsertRecord(sheet: TableModel, input: Map<String, String>): Boolean {
        val columns = columnDao.getColumnsByTableIdSync(sheet.id)
        if (columns.isEmpty()) return false

        val recordId = input["Record ID"]?.trim().orEmpty()
        val recordColumn = columns.find { it.name == "Record ID" }

        var rowId: Long? = null
        if (recordId.isNotBlank() && recordColumn != null) {
            rowId = cellDao.findCellsByColumnAndValue(recordColumn.id, recordId).firstOrNull()?.rowId
        }

        val isNewRow = rowId == null
        if (rowId == null) {
            rowId = findOrCreateWritableRow(sheet.id)
        }
        if (rowId == null) return false

        val data = input.toMutableMap()
        if (!isNewRow) {
            data.remove("Created At")
        }
        if (isNewRow && data["Created At"].isNullOrBlank()) {
            data["Created At"] = nowFormatted()
        }
        if (data["Updated At"].isNullOrBlank()) {
            data["Updated At"] = nowFormatted()
        }

        data.forEach { (columnName, value) ->
            val column = columns.find { it.name == columnName } ?: return@forEach
            if (!isNewRow && value.isBlank()) return@forEach
            upsertCell(rowId, column, value)
        }

        return true
    }

    private suspend fun findOrCreateWritableRow(tableId: Long): Long? {
        val rows = rowDao.getRowsByTableIdSync(tableId).sortedBy { it.orderIndex }
        for (row in rows) {
            val cells = cellDao.getCellsByRowIdSync(row.id)
            if (cells.isEmpty() || cells.all { it.value.isBlank() }) {
                return row.id
            }
        }

        val maxOrderIndex = rowDao.getMaxOrderIndex(tableId) ?: -1
        var firstNewRowId: Long? = null
        repeat(EXPAND_ROWS) { i ->
            val newRowId = rowDao.insertRow(RowModel(tableId = tableId, orderIndex = maxOrderIndex + i + 1))
            if (firstNewRowId == null) firstNewRowId = newRowId
        }
        tableDao.updateRowCount(tableId, rowDao.getRowCountSync(tableId))
        return firstNewRowId
    }

    private suspend fun upsertCell(rowId: Long, column: ColumnModel, value: String) {
        val existing = cellDao.getCellSync(rowId, column.id)
        if (existing == null) {
            cellDao.insertCell(
                CellModel(
                    rowId = rowId,
                    columnId = column.id,
                    value = value
                )
            )
        } else if (existing.value != value) {
            cellDao.updateCell(existing.copy(value = value))
        }
    }

    private suspend fun resolveTargetSheet(verification: PaymentVerification): TargetSheet {
        val orderId = verification.orderId.trim()
        if (orderId.isNotBlank()) {
            val method = paymentMethodManager.getPaymentMethodById(orderId)
            if (method != null) {
                return when (method.type) {
                    PaymentMethodType.QR_CODE -> TargetSheet.QR
                    PaymentMethodType.RAZORPAY -> TargetSheet.RAZORPAY
                    else -> TargetSheet.GENERAL
                }
            }
        }
        return if (isLikelyQrPayment(verification)) TargetSheet.QR else TargetSheet.GENERAL
    }

    private fun isLikelyQrPayment(verification: PaymentVerification): Boolean {
        val combinedText =
            listOf(
                    verification.reasoning,
                    verification.geminiRawResponse,
                    verification.customFieldsExpected,
                    verification.customFieldsExtracted,
                    verification.orderId
                )
                .joinToString(" ")
                .lowercase(Locale.ROOT)

        return combinedText.contains("qr") ||
            combinedText.contains("scan and pay") ||
            combinedText.contains("upi qr")
    }

    private fun buildVerificationAccountDetails(verification: PaymentVerification): String {
        val extracted = parseJsonMap(verification.customFieldsExtracted)
        val expected = parseJsonMap(verification.customFieldsExpected)

        val detailSet = linkedSetOf<String>()

        if (verification.upiId.isNotBlank()) {
            detailSet.add("UPI=${verification.upiId}")
        }

        extracted.forEach { (key, value) ->
            if (value.isNotBlank() && isBankOrAccountField(key)) {
                detailSet.add("$key=$value")
            }
        }
        expected.forEach { (key, value) ->
            if (value.isNotBlank() && isBankOrAccountField(key)) {
                detailSet.add("$key=$value")
            }
        }

        if (detailSet.isEmpty() && verification.expectedUpiId.isNotBlank()) {
            detailSet.add("UPI=${verification.expectedUpiId}")
        }

        return detailSet.joinToString(" | ").take(500)
    }

    private fun buildVerificationDescription(verification: PaymentVerification): String {
        val parts = mutableListOf<String>()
        if (verification.recommendation.isNotBlank()) {
            parts.add("Recommendation=${verification.recommendation}")
        }
        if (verification.paymentStatus.isNotBlank()) {
            parts.add("PaymentStatus=${verification.paymentStatus}")
        }
        if (verification.reasoning.isNotBlank()) {
            parts.add("Reason=${verification.reasoning.take(160)}")
        }
        if (verification.notes.isNotBlank()) {
            parts.add("Notes=${verification.notes.take(120)}")
        }
        if (parts.isEmpty()) {
            parts.add("Screenshot verification")
        }
        return parts.joinToString(" | ")
    }

    private fun buildVerificationHints(
        phoneNumber: String,
        verification: PaymentVerification,
        amountText: String
    ): String {
        val hints = linkedSetOf<String>()
        hints.add(phoneNumber)
        if (verification.customerPhone.isNotBlank()) hints.add(verification.customerPhone)
        if (verification.orderId.isNotBlank()) hints.add(verification.orderId)
        if (verification.transactionId.isNotBlank()) hints.add(verification.transactionId)
        if (verification.expectedName.isNotBlank()) hints.add(verification.expectedName)
        if (verification.payeeName.isNotBlank()) hints.add(verification.payeeName)
        if (verification.payerName.isNotBlank()) hints.add(verification.payerName)
        if (verification.expectedUpiId.isNotBlank()) hints.add(verification.expectedUpiId)
        if (verification.upiId.isNotBlank()) hints.add(verification.upiId)
        if (amountText.isNotBlank()) hints.add(amountText)

        parseJsonMap(verification.customFieldsExpected).values
            .filter { it.isNotBlank() }
            .take(6)
            .forEach { hints.add(it) }
        parseJsonMap(verification.customFieldsExtracted).values
            .filter { it.isNotBlank() }
            .take(6)
            .forEach { hints.add(it) }

        return hints.joinToString(" | ").take(800)
    }

    private fun buildRazorpayHints(
        phoneNumber: String,
        link: RazorPaymentManager.PaymentLinkInfo,
        customerName: String,
        amountText: String
    ): String {
        val hints = linkedSetOf<String>()
        hints.add(phoneNumber)
        if (link.customerContact?.isNotBlank() == true) hints.add(link.customerContact)
        if (customerName.isNotBlank()) hints.add(customerName)
        if (link.id.isNotBlank()) hints.add(link.id)
        if (amountText.isNotBlank()) hints.add(amountText)
        if (link.description.isNotBlank()) hints.add(link.description.take(60))
        return hints.joinToString(" | ").take(500)
    }

    private fun parseJsonMap(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return try {
            val json = JSONObject(raw)
            val result = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.optString(key, "")
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun isBankOrAccountField(key: String): Boolean {
        val lower = key.lowercase(Locale.ROOT)
        return lower.contains("bank") ||
            lower.contains("account") ||
            lower.contains("ifsc") ||
            lower.contains("swift") ||
            lower.contains("iban") ||
            lower.contains("upi")
    }

    private fun normalizeStatus(raw: String): String {
        val normalized = raw.trim().uppercase(Locale.ROOT)
        return when (normalized) {
            "PAID" -> "PAID"
            "APPROVE", "APPROVED", "VERIFIED" -> "APPROVED"
            "CREATED", "ISSUED", "PENDING" -> "PENDING"
            "MANUAL_REVIEW", "MANUAL REVIEW", "REVIEW" -> "MANUAL_REVIEW"
            "REJECT", "REJECTED" -> "REJECTED"
            "EXPIRED" -> "EXPIRED"
            "CANCELLED", "CANCELED" -> "CANCELLED"
            else -> {
                if (normalized.isBlank()) "UNKNOWN" else normalized.replace(' ', '_')
            }
        }
    }

    private fun formatAmount(amount: Double): String {
        return if (amount % 1.0 == 0.0) amount.toInt().toString()
        else String.format(Locale.US, "%.2f", amount)
    }

    private fun buildVerificationPaymentTime(verification: PaymentVerification): String {
        val date = verification.paymentDate.trim()
        val time = verification.paymentTime.trim()
        return when {
            date.isNotBlank() && time.isNotBlank() -> "$date $time"
            date.isNotBlank() -> date
            time.isNotBlank() -> time
            verification.screenshotTimestamp.isNotBlank() -> verification.screenshotTimestamp
            verification.uploadTimestamp.isNotBlank() -> verification.uploadTimestamp
            else -> nowFormatted()
        }
    }

    private fun formatCreatedAt(createdAt: String): String {
        val millis = parseCreatedAtMillis(createdAt) ?: return ""
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseCreatedAtMillis(createdAt: String): Long? {
        if (createdAt.isBlank()) return null

        val patterns =
            listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX"
            )

        for (pattern in patterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US)
                parser.timeZone = TimeZone.getTimeZone("UTC")
                val date = parser.parse(createdAt)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }

        return null
    }

    private fun nowFormatted(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
