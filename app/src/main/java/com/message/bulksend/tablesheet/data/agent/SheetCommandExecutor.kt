package com.message.bulksend.tablesheet.data.agent

import android.content.Context
import com.message.bulksend.tablesheet.data.TableSheetDatabase
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.ColumnType
import com.message.bulksend.tablesheet.data.models.RowModel
import com.message.bulksend.tablesheet.data.models.TableModel
import com.message.bulksend.tablesheet.data.repository.TableSheetRepository
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class SheetCommandExecutor(context: Context) {
    private val database = TableSheetDatabase.getDatabase(context.applicationContext)
    private val repository =
        TableSheetRepository(
            database.tableDao(),
            database.columnDao(),
            database.rowDao(),
            database.cellDao(),
            database.folderDao(),
            database.formulaDependencyDao(),
            database.cellSearchIndexDao(),
            database.rowVersionDao(),
            database.sheetTransactionDao(),
            database.filterViewDao(),
            database.conditionalFormatRuleDao(),
            database
        )

    data class CommandResult(
        val success: Boolean,
        val command: String,
        val message: String,
        val rows: List<Map<String, String>> = emptyList(),
        val aggregate: Map<String, String> = emptyMap(),
        val affectedRows: Int = 0,
        val rowId: Long? = null
    ) {
        fun summaryText(): String {
            if (!success) return "$command failed: $message"
            return when (command) {
                "SHEET_SELECT" -> "$command success: ${rows.size} row(s)"
                "SHEET_AGG" -> "$command success: $aggregate"
                "SHEET_UPSERT" -> "$command success: affected=$affectedRows rowId=${rowId ?: "-"}"
                "SHEET_BULK_UPSERT" -> "$command success: affected=$affectedRows"
                "SHEET_PIVOT" -> "$command success: ${rows.size} group(s)"
                else -> "$command success"
            }
        }
    }

    suspend fun execute(command: String, payload: JSONObject): CommandResult {
        val normalized = command.trim().uppercase(Locale.ROOT)
        return when (normalized) {
            "SHEET_SELECT" -> runSelect(payload)
            "SHEET_AGG" -> runAggregate(payload)
            "SHEET_UPSERT" -> runUpsert(payload)
            "SHEET_BULK_UPSERT" -> runBulkUpsert(payload)
            "SHEET_PIVOT" -> runPivot(payload)
            else ->
                CommandResult(
                    success = false,
                    command = normalized,
                    message = "Unsupported command: $normalized"
                )
        }
    }

    private suspend fun runSelect(payload: JSONObject): CommandResult {
        val resolved = resolveTable(payload) ?: return tableResolveFailure("SHEET_SELECT")
        val schemaColumns = repository.getColumnsByTableIdSync(resolved.id).sortedBy { it.orderIndex }
        val schemaByName = schemaColumns.associateBy { normalizeColumnKey(it.name) }

        val selectedColumnsResult = resolveRequestedColumns(payload.optJSONArray("columns"), schemaByName)
        if (!selectedColumnsResult.success) {
            return CommandResult(
                success = false,
                command = "SHEET_SELECT",
                message = selectedColumnsResult.message
            )
        }

        val whereFilters = parseFilters(payload.optJSONObject("where"))
        val containsFilters = parseFilters(payload.optJSONObject("contains"))
        val resolvedWhere =
            resolveFilterMapByColumnId(
                filters = whereFilters,
                schema = schemaByName,
                command = "SHEET_SELECT",
                label = "where"
            )
        if (!resolvedWhere.success) {
            return CommandResult(
                success = false,
                command = "SHEET_SELECT",
                message = resolvedWhere.message
            )
        }
        val resolvedContains =
            resolveFilterMapByColumnId(
                filters = containsFilters,
                schema = schemaByName,
                command = "SHEET_SELECT",
                label = "contains"
            )
        if (!resolvedContains.success) {
            return CommandResult(
                success = false,
                command = "SHEET_SELECT",
                message = resolvedContains.message
            )
        }

        val filteredRowIds =
            repository.queryRowIdsByFilterPlan(
                tableId = resolved.id,
                exactFiltersByColumnId = resolvedWhere.filtersByColumnId,
                containsFiltersByColumnId = resolvedContains.filtersByColumnId
            )
        if (filteredRowIds != null && filteredRowIds.isEmpty()) {
            return CommandResult(
                success = true,
                command = "SHEET_SELECT",
                message = "Selected 0 row(s) from '${resolved.name}'",
                rows = emptyList(),
                affectedRows = 0
            )
        }
        val data = loadTableData(resolved, filteredRowIds)
        val limit = payload.optInt("limit", 50).coerceIn(1, 500)
        val orderByColumn = payload.optString("orderBy", "").trim()
        val orderDirection = payload.optString("order", "ASC").trim().uppercase(Locale.ROOT)

        var filteredRows =
            data.rowsWithValues.filter { row ->
                matchesExactFilters(row.values, whereFilters, data.columnsByName) &&
                    matchesContainsFilters(row.values, containsFilters, data.columnsByName)
            }

        if (orderByColumn.isNotBlank()) {
            val normalizedOrderColumn = normalizeColumnKey(orderByColumn)
            val schemaColumn = data.columnsByName[normalizedOrderColumn]
            if (schemaColumn == null) {
                return CommandResult(
                    success = false,
                    command = "SHEET_SELECT",
                    message = "Unknown orderBy column '$orderByColumn'"
                )
            }
            filteredRows =
                filteredRows.sortedWith { left, right ->
                    compareCellValues(
                        leftRaw = left.values[schemaColumn.name].orEmpty(),
                        rightRaw = right.values[schemaColumn.name].orEmpty(),
                        columnTypeRaw = schemaColumn.type
                    )
                }
            if (orderDirection == "DESC") {
                filteredRows = filteredRows.asReversed()
            }
        }

        val requestedColumns =
            selectedColumnsResult.columns?.ifEmpty { data.columnsOrdered.map { it.name } }
                ?: data.columnsOrdered.map { it.name }

        val resultRows =
            filteredRows
                .take(limit)
                .map { row ->
                    requestedColumns.associateWith { columnName ->
                        row.values[columnName].orEmpty()
                    }
                }

        return CommandResult(
            success = true,
            command = "SHEET_SELECT",
            message = "Selected ${resultRows.size} row(s) from '${resolved.name}'",
            rows = resultRows,
            affectedRows = resultRows.size
        )
    }

    private suspend fun runAggregate(payload: JSONObject): CommandResult {
        val resolved = resolveTable(payload) ?: return tableResolveFailure("SHEET_AGG")
        val schemaColumns = repository.getColumnsByTableIdSync(resolved.id).sortedBy { it.orderIndex }
        val schemaByName = schemaColumns.associateBy { normalizeColumnKey(it.name) }
        val whereFilters = parseFilters(payload.optJSONObject("where"))
        val containsFilters = parseFilters(payload.optJSONObject("contains"))
        val resolvedWhere =
            resolveFilterMapByColumnId(
                filters = whereFilters,
                schema = schemaByName,
                command = "SHEET_AGG",
                label = "where"
            )
        if (!resolvedWhere.success) {
            return CommandResult(
                success = false,
                command = "SHEET_AGG",
                message = resolvedWhere.message
            )
        }
        val resolvedContains =
            resolveFilterMapByColumnId(
                filters = containsFilters,
                schema = schemaByName,
                command = "SHEET_AGG",
                label = "contains"
            )
        if (!resolvedContains.success) {
            return CommandResult(
                success = false,
                command = "SHEET_AGG",
                message = resolvedContains.message
            )
        }
        val filteredRowIds =
            repository.queryRowIdsByFilterPlan(
                tableId = resolved.id,
                exactFiltersByColumnId = resolvedWhere.filtersByColumnId,
                containsFiltersByColumnId = resolvedContains.filtersByColumnId
            )
        val data = loadTableData(resolved, filteredRowIds)

        val operation =
            payload.optString("operation", payload.optString("agg", "COUNT"))
                .trim()
                .uppercase(Locale.ROOT)

        val filteredRows =
            data.rowsWithValues.filter { row ->
                matchesExactFilters(row.values, whereFilters, data.columnsByName) &&
                    matchesContainsFilters(row.values, containsFilters, data.columnsByName)
            }

        val aggregate =
            when (operation) {
                "COUNT" -> mapOf("COUNT" to filteredRows.size.toString())

                "SUM", "AVG", "MIN", "MAX" -> {
                    val columnNameRaw = payload.optString("column", "").trim()
                    val targetColumn = resolveSingleColumn(columnNameRaw, data.columnsByName)
                        ?: return CommandResult(
                            success = false,
                            command = "SHEET_AGG",
                            message = "Unknown numeric column '$columnNameRaw'"
                        )

                    val numbers =
                        filteredRows.mapNotNull { row ->
                            row.values[targetColumn.name]
                                ?.replace(",", "")
                                ?.replace(Regex("[^0-9+\\-.]"), "")
                                ?.trim()
                                ?.toDoubleOrNull()
                        }
                    val value =
                        when (operation) {
                            "SUM" -> numbers.sum()
                            "AVG" -> if (numbers.isNotEmpty()) numbers.average() else 0.0
                            "MIN" -> numbers.minOrNull() ?: 0.0
                            "MAX" -> numbers.maxOrNull() ?: 0.0
                            else -> 0.0
                        }
                    mapOf(operation to formatNumber(value))
                }

                "COUNTIF" -> {
                    val columnNameRaw = payload.optString("column", "").trim()
                    val criteria = payload.optString("criteria", "").trim()
                    val targetColumn = resolveSingleColumn(columnNameRaw, data.columnsByName)
                        ?: return CommandResult(
                            success = false,
                            command = "SHEET_AGG",
                            message = "Unknown COUNTIF column '$columnNameRaw'"
                        )
                    val matched =
                        filteredRows.count { row ->
                            val rawValue = row.values[targetColumn.name].orEmpty()
                            matchesCriteria(rawValue, criteria)
                        }
                    mapOf("COUNTIF" to matched.toString())
                }

                else ->
                    return CommandResult(
                        success = false,
                        command = "SHEET_AGG",
                        message = "Unsupported operation '$operation'"
                    )
            }

        return CommandResult(
            success = true,
            command = "SHEET_AGG",
            message = "Aggregate computed on '${resolved.name}'",
            aggregate = aggregate,
            affectedRows = filteredRows.size
        )
    }

    private suspend fun runUpsert(payload: JSONObject): CommandResult {
        val resolved = resolveTable(payload) ?: return tableResolveFailure("SHEET_UPSERT")
        val data = loadTableData(resolved)

        val keyJson = payload.optJSONObject("key")
        val valuesJson = payload.optJSONObject("values")
        if (keyJson == null || valuesJson == null) {
            return CommandResult(
                success = false,
                command = "SHEET_UPSERT",
                message = "Payload must contain both 'key' and 'values' objects"
            )
        }

        val keyMap = parseFilters(keyJson)
        val valueMap = parseFilters(valuesJson)
        if (keyMap.isEmpty()) {
            return CommandResult(
                success = false,
                command = "SHEET_UPSERT",
                message = "'key' cannot be empty"
            )
        }
        if (valueMap.isEmpty()) {
            return CommandResult(
                success = false,
                command = "SHEET_UPSERT",
                message = "'values' cannot be empty"
            )
        }

        val unknownKeyColumns =
            keyMap.keys.filter { data.columnsByName[normalizeColumnKey(it)] == null }
        if (unknownKeyColumns.isNotEmpty()) {
            return CommandResult(
                success = false,
                command = "SHEET_UPSERT",
                message = "Unknown key columns: ${unknownKeyColumns.joinToString()}"
            )
        }

        val unknownValueColumns =
            valueMap.keys.filter { data.columnsByName[normalizeColumnKey(it)] == null }
        if (unknownValueColumns.isNotEmpty()) {
            return CommandResult(
                success = false,
                command = "SHEET_UPSERT",
                message = "Unknown value columns: ${unknownValueColumns.joinToString()}"
            )
        }

        val rowToUpdate =
            data.rowsWithValues.firstOrNull { row ->
                keyMap.all { (column, expected) ->
                    val schemaColumn = data.columnsByName[normalizeColumnKey(column)] ?: return@all false
                    row.values[schemaColumn.name].orEmpty().trim()
                        .equals(expected.trim(), ignoreCase = true)
                }
            }

        val rowId =
            if (rowToUpdate != null) {
                rowToUpdate.row.id
            } else {
                repository.addRow(resolved.id)
            }

        val mergedInput = linkedMapOf<String, String>().apply {
            putAll(keyMap)
            putAll(valueMap)
        }

        val updatesByColumnId = linkedMapOf<Long, String>()
        mergedInput.forEach { (columnName, value) ->
            val schemaColumn = data.columnsByName[normalizeColumnKey(columnName)] ?: return@forEach
            updatesByColumnId[schemaColumn.id] = value
        }
        if (updatesByColumnId.isNotEmpty()) {
            repository.updateRowValues(rowId, updatesByColumnId)
        }

        return CommandResult(
            success = true,
            command = "SHEET_UPSERT",
            message = if (rowToUpdate != null) "Existing row updated" else "New row inserted",
            affectedRows = 1,
            rowId = rowId
        )
    }

    private suspend fun runBulkUpsert(payload: JSONObject): CommandResult {
        val resolved = resolveTable(payload) ?: return tableResolveFailure("SHEET_BULK_UPSERT")
        val data = loadTableData(resolved)

        val rowsJson =
            payload.optJSONArray("rows")
                ?: payload.optJSONArray("records")
                ?: payload.optJSONArray("items")
        if (rowsJson == null || rowsJson.length() == 0) {
            return CommandResult(
                success = false,
                command = "SHEET_BULK_UPSERT",
                message = "Payload must contain non-empty 'rows' (or 'records'/'items') array"
            )
        }

        val keyColumnsRaw = parseStringArray(payload.optJSONArray("keyColumns"))
        val unknownKeyColumns =
            keyColumnsRaw.filter { raw ->
                data.columnsByName[normalizeColumnKey(raw)] == null
            }
        if (unknownKeyColumns.isNotEmpty()) {
            return CommandResult(
                success = false,
                command = "SHEET_BULK_UPSERT",
                message = "Unknown keyColumns: ${unknownKeyColumns.joinToString()}"
            )
        }
        val keyColumnsNormalized = keyColumnsRaw.map { normalizeColumnKey(it) }

        val maxRows =
            payload.optInt("maxRows", BULK_UPSERT_DEFAULT_MAX_ROWS)
                .coerceIn(1, BULK_UPSERT_HARD_MAX_ROWS)
        val processCount = minOf(rowsJson.length(), maxRows)
        val skippedByLimit = rowsJson.length() - processCount

        val workingRows =
            data.rowsWithValues.map { row ->
                WorkingRow(
                    rowId = row.row.id,
                    values = row.values.toMutableMap()
                )
            }.toMutableList()

        var inserted = 0
        var updated = 0
        var succeeded = 0
        val failures = mutableListOf<String>()

        for (index in 0 until processCount) {
            val rowJson = rowsJson.optJSONObject(index)
            if (rowJson == null) {
                failures += "row ${index + 1}: item must be JSON object"
                continue
            }

            val parsed =
                parseBulkUpsertRow(
                    rowJson = rowJson,
                    keyColumnsNormalized = keyColumnsNormalized,
                    schema = data.columnsByName
                )
            if (!parsed.success) {
                failures += "row ${index + 1}: ${parsed.message}"
                continue
            }

            val keyMap = parsed.keyMap
            val valueMap = parsed.valueMap
            val mergedInput = linkedMapOf<String, String>().apply {
                putAll(keyMap)
                putAll(valueMap)
            }
            if (mergedInput.isEmpty()) {
                failures += "row ${index + 1}: no writable values found"
                continue
            }

            val updatesByColumnId = linkedMapOf<Long, String>()
            mergedInput.forEach { (columnName, value) ->
                val schemaColumn = data.columnsByName[normalizeColumnKey(columnName)] ?: return@forEach
                updatesByColumnId[schemaColumn.id] = value
            }
            if (updatesByColumnId.isEmpty()) {
                failures += "row ${index + 1}: mapped updates are empty"
                continue
            }

            var updatedExisting = false
            runCatching {
                val existingRow =
                    workingRows.firstOrNull { working ->
                        keyMap.all { (column, expected) ->
                            val schemaColumn = data.columnsByName[normalizeColumnKey(column)] ?: return@all false
                            working.values[schemaColumn.name].orEmpty().trim()
                                .equals(expected.trim(), ignoreCase = true)
                        }
                    }
                updatedExisting = existingRow != null
                val targetRow =
                    if (existingRow != null) {
                        existingRow
                    } else {
                        val rowId = repository.addRow(resolved.id)
                        val emptyValues =
                            data.columnsOrdered.associate { column ->
                                column.name to ""
                            }.toMutableMap()
                        WorkingRow(rowId = rowId, values = emptyValues).also { created ->
                            workingRows += created
                        }
                    }
                repository.updateRowValues(
                    rowId = targetRow.rowId,
                    valuesByColumnId = updatesByColumnId,
                    source = BULK_UPSERT_SOURCE
                )
                targetRow
            }.onSuccess { targetRow ->
                mergedInput.forEach { (columnName, value) ->
                    val schemaColumn = data.columnsByName[normalizeColumnKey(columnName)] ?: return@forEach
                    targetRow.values[schemaColumn.name] = value
                }
                succeeded += 1
                if (updatedExisting) {
                    updated += 1
                } else {
                    inserted += 1
                }
            }.onFailure { error ->
                failures += "row ${index + 1}: ${error.message ?: "write failed"}"
            }
        }

        val failed = processCount - succeeded
        val limitSuffix = if (skippedByLimit > 0) " skippedByLimit=$skippedByLimit" else ""
        val sampleErrors =
            failures.take(3).joinToString(" | ").takeIf { it.isNotBlank() }
                ?.let { " errors=$it" }
                .orEmpty()

        if (succeeded == 0) {
            return CommandResult(
                success = false,
                command = "SHEET_BULK_UPSERT",
                message = "No rows written. processed=$processCount failed=$failed$limitSuffix$sampleErrors",
                affectedRows = 0
            )
        }

        return CommandResult(
            success = true,
            command = "SHEET_BULK_UPSERT",
            message = "Bulk upsert done. processed=$processCount inserted=$inserted updated=$updated failed=$failed$limitSuffix$sampleErrors",
            affectedRows = succeeded
        )
    }

    private suspend fun runPivot(payload: JSONObject): CommandResult {
        val resolved = resolveTable(payload) ?: return tableResolveFailure("SHEET_PIVOT")
        val schemaColumns = repository.getColumnsByTableIdSync(resolved.id).sortedBy { it.orderIndex }
        val schemaByName = schemaColumns.associateBy { normalizeColumnKey(it.name) }
        val whereFilters = parseFilters(payload.optJSONObject("where"))
        val containsFilters = parseFilters(payload.optJSONObject("contains"))
        val resolvedWhere =
            resolveFilterMapByColumnId(
                filters = whereFilters,
                schema = schemaByName,
                command = "SHEET_PIVOT",
                label = "where"
            )
        if (!resolvedWhere.success) {
            return CommandResult(
                success = false,
                command = "SHEET_PIVOT",
                message = resolvedWhere.message
            )
        }
        val resolvedContains =
            resolveFilterMapByColumnId(
                filters = containsFilters,
                schema = schemaByName,
                command = "SHEET_PIVOT",
                label = "contains"
            )
        if (!resolvedContains.success) {
            return CommandResult(
                success = false,
                command = "SHEET_PIVOT",
                message = resolvedContains.message
            )
        }
        val filteredRowIds =
            repository.queryRowIdsByFilterPlan(
                tableId = resolved.id,
                exactFiltersByColumnId = resolvedWhere.filtersByColumnId,
                containsFiltersByColumnId = resolvedContains.filtersByColumnId
            )
        val data = loadTableData(resolved, filteredRowIds)

        val groupByRaw = payload.optString("groupBy", "").trim()
        val groupByColumn = resolveSingleColumn(groupByRaw, data.columnsByName)
            ?: return CommandResult(
                success = false,
                command = "SHEET_PIVOT",
                message = "Unknown groupBy column '$groupByRaw'"
            )

        val operation = payload.optString("operation", "COUNT").trim().uppercase(Locale.ROOT)
        val valueColumnRaw = payload.optString("valueColumn", payload.optString("column", "")).trim()
        val valueColumn =
            if (operation == "COUNT") null
            else resolveSingleColumn(valueColumnRaw, data.columnsByName)
                ?: return CommandResult(
                    success = false,
                    command = "SHEET_PIVOT",
                    message = "Unknown numeric value column '$valueColumnRaw'"
                )

        val filteredRows =
            data.rowsWithValues.filter { row ->
                matchesExactFilters(row.values, whereFilters, data.columnsByName) &&
                    matchesContainsFilters(row.values, containsFilters, data.columnsByName)
            }

        val grouped = filteredRows.groupBy { row ->
            row.values[groupByColumn.name].orEmpty().ifBlank { "(blank)" }
        }

        val pivotRows =
            grouped.entries.map { (groupValue, rows) ->
                val metric =
                    when (operation) {
                        "COUNT" -> rows.size.toString()
                        "SUM", "AVG", "MIN", "MAX" -> {
                            val numbers =
                                rows.mapNotNull { row ->
                                    row.values[valueColumn!!.name]
                                        ?.replace(",", "")
                                        ?.replace(Regex("[^0-9+\\-.]"), "")
                                        ?.trim()
                                        ?.toDoubleOrNull()
                                }
                            val value =
                                when (operation) {
                                    "SUM" -> numbers.sum()
                                    "AVG" -> if (numbers.isNotEmpty()) numbers.average() else 0.0
                                    "MIN" -> numbers.minOrNull() ?: 0.0
                                    "MAX" -> numbers.maxOrNull() ?: 0.0
                                    else -> 0.0
                                }
                            formatNumber(value)
                        }

                        else -> rows.size.toString()
                    }

                mapOf(
                    groupByColumn.name to groupValue,
                    operation to metric
                )
            }.sortedBy { it[groupByColumn.name].orEmpty() }

        return CommandResult(
            success = true,
            command = "SHEET_PIVOT",
            message = "Pivot computed on '${resolved.name}'",
            rows = pivotRows,
            affectedRows = pivotRows.size
        )
    }

    private suspend fun resolveTable(payload: JSONObject): TableModel? {
        val tableId = payload.optLong("tableId", -1L)
        if (tableId > 0) {
            return repository.getTableById(tableId)
        }

        val tableName = payload.optString("table", payload.optString("tableName", "")).trim()
        if (tableName.isBlank()) return null
        val tableDao = database.tableDao()
        tableDao.getTableByName(tableName)?.let { return it }
        tableDao.getTableByNameNormalized(tableName)?.let { return it }

        val collapsed = tableName.replace(Regex("\\s+"), " ").trim()
        if (collapsed.isNotEmpty() && !collapsed.equals(tableName, ignoreCase = false)) {
            tableDao.getTableByName(collapsed)?.let { return it }
            tableDao.getTableByNameNormalized(collapsed)?.let { return it }
        }

        val normalizedTarget = normalizeTableNameForMatch(tableName)
        return tableDao
            .getAllTablesSync()
            .firstOrNull { normalizeTableNameForMatch(it.name) == normalizedTarget }
    }

    private fun tableResolveFailure(command: String): CommandResult {
        return CommandResult(
            success = false,
            command = command,
            message = "Table not found. Provide valid 'tableId' or 'table' name."
        )
    }

    private suspend fun loadTableData(
        table: TableModel,
        filteredRowIds: Set<Long>? = null
    ): LoadedTableData {
        val columns = repository.getColumnsByTableIdSync(table.id).sortedBy { it.orderIndex }
        val rows =
            if (filteredRowIds == null) {
                repository.getRowsByTableIdSync(table.id).sortedBy { it.orderIndex }
            } else {
                repository.getRowsByTableIdAndIdsSync(table.id, filteredRowIds.toList())
                    .sortedBy { it.orderIndex }
            }
        val cellsByCoordinate =
            if (rows.isEmpty()) {
                emptyMap()
            } else {
                repository.getCellsByRowIds(rows.map { it.id }).associateBy { it.rowId to it.columnId }
            }

        val rowsWithValues =
            rows.map { row ->
                val values =
                    columns.associate { column ->
                        val cellValue = cellsByCoordinate[row.id to column.id]?.value.orEmpty()
                        column.name to cellValue
                    }
                RowWithValues(row = row, values = values)
            }

        val columnsByName =
            columns.associateBy { column ->
                normalizeColumnKey(column.name)
            }

        return LoadedTableData(
            table = table,
            columnsOrdered = columns,
            columnsByName = columnsByName,
            rowsWithValues = rowsWithValues
        )
    }

    private fun resolveRequestedColumns(
        columnsJson: JSONArray?,
        schema: Map<String, ColumnModel>
    ): RequestedColumnsResult {
        if (columnsJson == null || columnsJson.length() == 0) {
            return RequestedColumnsResult(success = true, columns = null, message = "")
        }

        val resolved = mutableListOf<String>()
        val unknown = mutableListOf<String>()
        for (index in 0 until columnsJson.length()) {
            val raw = columnsJson.optString(index).trim()
            if (raw.isBlank()) continue
            val schemaColumn = schema[normalizeColumnKey(raw)]
            if (schemaColumn == null) {
                unknown += raw
            } else {
                resolved += schemaColumn.name
            }
        }

        if (unknown.isNotEmpty()) {
            return RequestedColumnsResult(
                success = false,
                columns = null,
                message = "Unknown columns: ${unknown.joinToString()}"
            )
        }
        return RequestedColumnsResult(success = true, columns = resolved.distinct(), message = "")
    }

    private fun resolveSingleColumn(raw: String, schema: Map<String, ColumnModel>): ColumnModel? {
        if (raw.isBlank()) return null
        return schema[normalizeColumnKey(raw)]
    }

    private fun parseFilters(json: JSONObject?): Map<String, String> {
        if (json == null) return emptyMap()
        val filters = linkedMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.optString(key).trim()
            if (key.isNotBlank() && value.isNotBlank()) {
                filters[key.trim()] = value
            }
        }
        return filters
    }

    private fun parseStringArray(array: JSONArray?): List<String> {
        if (array == null || array.length() == 0) return emptyList()
        val values = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val token = array.optString(index).trim()
            if (token.isNotBlank()) {
                values += token
            }
        }
        return values
    }

    private fun parseBulkUpsertRow(
        rowJson: JSONObject,
        keyColumnsNormalized: List<String>,
        schema: Map<String, ColumnModel>
    ): BulkUpsertRowParseResult {
        val keyObject = rowJson.optJSONObject("key")
        val valueObject = rowJson.optJSONObject("values")
        if (keyObject != null || valueObject != null) {
            if (keyObject == null || valueObject == null) {
                return BulkUpsertRowParseResult(
                    success = false,
                    message = "both 'key' and 'values' must be present"
                )
            }

            val keyMap = parseFilters(keyObject)
            val valueMap = parseFilters(valueObject)
            if (keyMap.isEmpty()) {
                return BulkUpsertRowParseResult(success = false, message = "'key' cannot be empty")
            }
            if (valueMap.isEmpty()) {
                return BulkUpsertRowParseResult(success = false, message = "'values' cannot be empty")
            }

            val unknownColumns =
                (keyMap.keys + valueMap.keys).filter { name ->
                    schema[normalizeColumnKey(name)] == null
                }
            if (unknownColumns.isNotEmpty()) {
                return BulkUpsertRowParseResult(
                    success = false,
                    message = "unknown columns: ${unknownColumns.joinToString()}"
                )
            }

            return BulkUpsertRowParseResult(
                success = true,
                keyMap = keyMap,
                valueMap = valueMap
            )
        }

        val flatMap = parseFilters(rowJson)
        if (flatMap.isEmpty()) {
            return BulkUpsertRowParseResult(success = false, message = "row object is empty")
        }

        val unknownColumns =
            flatMap.keys.filter { name ->
                schema[normalizeColumnKey(name)] == null
            }
        if (unknownColumns.isNotEmpty()) {
            return BulkUpsertRowParseResult(
                success = false,
                message = "unknown columns: ${unknownColumns.joinToString()}"
            )
        }

        if (keyColumnsNormalized.isEmpty()) {
            return BulkUpsertRowParseResult(
                success = false,
                message = "provide key+values or top-level keyColumns for flat rows"
            )
        }

        val rowEntriesByNormalized =
            flatMap.entries.associateBy { (key, _) ->
                normalizeColumnKey(key)
            }
        val missingKeys =
            keyColumnsNormalized.filter { required ->
                rowEntriesByNormalized[required] == null
            }
        if (missingKeys.isNotEmpty()) {
            return BulkUpsertRowParseResult(
                success = false,
                message = "missing key columns: ${missingKeys.joinToString()}"
            )
        }

        val keyMap = linkedMapOf<String, String>()
        keyColumnsNormalized.forEach { normalized ->
            val entry = rowEntriesByNormalized[normalized] ?: return@forEach
            keyMap[entry.key] = entry.value
        }
        val valueMap =
            flatMap.filterKeys { key ->
                normalizeColumnKey(key) !in keyColumnsNormalized
            }
        if (valueMap.isEmpty()) {
            return BulkUpsertRowParseResult(
                success = false,
                message = "flat row must contain at least one non-key value"
            )
        }

        return BulkUpsertRowParseResult(
            success = true,
            keyMap = keyMap,
            valueMap = valueMap
        )
    }

    private fun resolveFilterMapByColumnId(
        filters: Map<String, String>,
        schema: Map<String, ColumnModel>,
        command: String,
        label: String
    ): ResolvedFilterMap {
        if (filters.isEmpty()) return ResolvedFilterMap(success = true, filtersByColumnId = emptyMap(), message = "")

        val resolved = linkedMapOf<Long, String>()
        filters.forEach { (columnName, value) ->
            val column = schema[normalizeColumnKey(columnName)]
                ?: return ResolvedFilterMap(
                    success = false,
                    filtersByColumnId = emptyMap(),
                    message = "Unknown $label column '$columnName' for $command"
                )
            resolved[column.id] = value
        }
        return ResolvedFilterMap(success = true, filtersByColumnId = resolved, message = "")
    }

    private fun matchesExactFilters(
        rowValues: Map<String, String>,
        filters: Map<String, String>,
        schema: Map<String, ColumnModel>
    ): Boolean {
        if (filters.isEmpty()) return true
        return filters.all { (columnName, expectedValue) ->
            val schemaColumn = schema[normalizeColumnKey(columnName)] ?: return@all false
            rowValues[schemaColumn.name].orEmpty().trim()
                .equals(expectedValue.trim(), ignoreCase = true)
        }
    }

    private fun matchesContainsFilters(
        rowValues: Map<String, String>,
        filters: Map<String, String>,
        schema: Map<String, ColumnModel>
    ): Boolean {
        if (filters.isEmpty()) return true
        return filters.all { (columnName, needle) ->
            val schemaColumn = schema[normalizeColumnKey(columnName)] ?: return@all false
            rowValues[schemaColumn.name].orEmpty().contains(needle, ignoreCase = true)
        }
    }

    private fun normalizeColumnKey(raw: String): String {
        return raw.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    }

    private fun normalizeTableNameForMatch(raw: String): String {
        return raw.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    }

    private fun compareCellValues(
        leftRaw: String,
        rightRaw: String,
        columnTypeRaw: String?
    ): Int {
        val left = leftRaw.trim()
        val right = rightRaw.trim()
        if (left.isEmpty() && right.isEmpty()) return 0
        if (left.isEmpty()) return -1
        if (right.isEmpty()) return 1

        val normalizedType = columnTypeRaw?.trim()?.uppercase(Locale.ROOT).orEmpty()
        when (normalizedType) {
            ColumnType.INTEGER, ColumnType.DECIMAL, ColumnType.AMOUNT -> {
                val leftNum = parseNumericValue(left)
                val rightNum = parseNumericValue(right)
                if (leftNum != null && rightNum != null) return leftNum.compareTo(rightNum)
            }
            ColumnType.CHECKBOX -> {
                val leftBool = left.equals("true", ignoreCase = true)
                val rightBool = right.equals("true", ignoreCase = true)
                return leftBool.compareTo(rightBool)
            }
            ColumnType.DATE, "DATEONLY", ColumnType.DATETIME, ColumnType.TIME, "TIME" -> {
                val leftTime = parseDateSortValue(left)
                val rightTime = parseDateSortValue(right)
                if (leftTime != null && rightTime != null) return leftTime.compareTo(rightTime)
            }
        }

        val leftNum = parseNumericValue(left)
        val rightNum = parseNumericValue(right)
        if (leftNum != null && rightNum != null) return leftNum.compareTo(rightNum)

        return left.compareTo(right, ignoreCase = true)
    }

    private fun parseNumericValue(raw: String): Double? {
        return raw
            .replace(",", "")
            .replace(Regex("[^0-9+\\-.]"), "")
            .trim()
            .toDoubleOrNull()
    }

    private fun parseDateSortValue(raw: String): Long? {
        val token = raw.trim()
        if (token.isBlank()) return null
        token.toLongOrNull()?.let { epoch ->
            if (epoch > 0L) return epoch
        }
        val patterns =
            listOf(
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "MM/dd/yyyy",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "MM/dd/yyyy HH:mm",
                "dd/MM/yyyy HH:mm:ss",
                "MM/dd/yyyy HH:mm:ss",
                "yyyy/MM/dd",
                "yyyyMMdd",
                "HH:mm",
                "yyyy-MM-dd'T'HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss"
            )
        patterns.forEach { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.getDefault()).apply {
                    isLenient = false
                }.parse(token)
            }.getOrNull()?.let { parsed ->
                return parsed.time
            }
        }
        return null
    }

    private fun matchesCriteria(rawValue: String, criteriaRaw: String): Boolean {
        val criteria = criteriaRaw.trim()
        if (criteria.isBlank()) return rawValue.isBlank()
        val operators = listOf(">=", "<=", "<>", ">", "<", "=")
        val operator = operators.firstOrNull { criteria.startsWith(it) } ?: "="
        val rhs = criteria.removePrefix(operator).trim().trim('"')

        val leftNumber =
            rawValue
                .replace(",", "")
                .replace(Regex("[^0-9+\\-.]"), "")
                .trim()
                .toDoubleOrNull()
        val rightNumber =
            rhs
                .replace(",", "")
                .replace(Regex("[^0-9+\\-.]"), "")
                .trim()
                .toDoubleOrNull()

        if (leftNumber != null && rightNumber != null) {
            return when (operator) {
                ">" -> leftNumber > rightNumber
                "<" -> leftNumber < rightNumber
                ">=" -> leftNumber >= rightNumber
                "<=" -> leftNumber <= rightNumber
                "<>" -> leftNumber != rightNumber
                else -> leftNumber == rightNumber
            }
        }

        return when (operator) {
            "<>" -> !rawValue.equals(rhs, ignoreCase = true)
            else -> rawValue.equals(rhs, ignoreCase = true)
        }
    }

    private fun formatNumber(value: Double): String {
        if (value == value.toLong().toDouble()) return value.toLong().toString()
        return value.toString().trimEnd('0').trimEnd('.')
    }

    private data class RowWithValues(
        val row: RowModel,
        val values: Map<String, String>
    )

    private data class LoadedTableData(
        val table: TableModel,
        val columnsOrdered: List<ColumnModel>,
        val columnsByName: Map<String, ColumnModel>,
        val rowsWithValues: List<RowWithValues>
    )

    private data class RequestedColumnsResult(
        val success: Boolean,
        val columns: List<String>?,
        val message: String
    )

    private data class ResolvedFilterMap(
        val success: Boolean,
        val filtersByColumnId: Map<Long, String>,
        val message: String
    )

    private data class BulkUpsertRowParseResult(
        val success: Boolean,
        val keyMap: Map<String, String> = emptyMap(),
        val valueMap: Map<String, String> = emptyMap(),
        val message: String = ""
    )

    private data class WorkingRow(
        val rowId: Long,
        val values: MutableMap<String, String>
    )

    private companion object {
        const val BULK_UPSERT_DEFAULT_MAX_ROWS = 1000
        const val BULK_UPSERT_HARD_MAX_ROWS = 10_000
        const val BULK_UPSERT_SOURCE = "AI_BULK_UPSERT"
    }
}

