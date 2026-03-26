package com.message.bulksend.aiagent.tools.ownerassist

import android.content.Context
import com.message.bulksend.tablesheet.data.TableSheetDatabase
import com.message.bulksend.tablesheet.data.repository.TableSheetRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class OwnerAssistContextBuilder(
    context: Context
) {
    private val appContext = context.applicationContext
    private val database = TableSheetDatabase.getDatabase(appContext)
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

    suspend fun buildSheetContext(
        maxTables: Int = 8,
        maxColumnsPerTable: Int = 10
    ): String {
        return runCatching {
            val safeTableCount = maxTables.coerceIn(1, 20)
            val safeColumnCount = maxColumnsPerTable.coerceIn(1, 20)
            val tables = database.tableDao().getAllTables().first().take(safeTableCount)

            if (tables.isEmpty()) {
                return "No TableSheet data available."
            }

            val headerTime =
                LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
                )

            buildString {
                append("Owner Assist Sheet Context\n")
                append("Now: $headerTime\n")
                append("Available tables and columns:\n")

                tables.forEachIndexed { index, table ->
                    val columns =
                        repository.getColumnsByTableIdSync(table.id)
                            .sortedBy { it.orderIndex }
                            .mapNotNull { column ->
                                column.name.trim().takeIf { it.isNotBlank() }
                            }
                            .take(safeColumnCount)

                    val columnSummary =
                        if (columns.isEmpty()) "(no columns)"
                        else columns.joinToString(", ")

                    append("${index + 1}. ${table.name} -> $columnSummary\n")
                }
            }.trim()
        }.getOrElse { error ->
            "Table context unavailable: ${error.message ?: "unknown error"}"
        }
    }
}
