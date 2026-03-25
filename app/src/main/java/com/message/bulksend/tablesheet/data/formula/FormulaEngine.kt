package com.message.bulksend.tablesheet.data.formula

import com.message.bulksend.tablesheet.data.models.CellModel
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.RowModel
import kotlin.math.abs

data class FormulaDependencyRef(
    val rowId: Long,
    val columnId: Long,
    val address: String
)

data class FormulaEvaluationResult(
    val value: String,
    val dependencies: List<FormulaDependencyRef>
)

class FormulaEngine(
    rows: List<RowModel>,
    columns: List<ColumnModel>,
    private val cellsByCoordinate: Map<Pair<Long, Long>, CellModel>
) {
    private val sortedRows = rows.sortedBy { it.orderIndex }
    private val sortedColumns = columns.sortedBy { it.orderIndex }
    private val rowIdByOneBasedIndex = sortedRows.mapIndexed { idx, row -> (idx + 1) to row.id }.toMap()
    private val rowOneBasedIndexById = sortedRows.mapIndexed { idx, row -> row.id to (idx + 1) }.toMap()
    private val columnIdByZeroBasedIndex = sortedColumns.mapIndexed { idx, column -> idx to column.id }.toMap()
    private val columnZeroBasedIndexById = sortedColumns.mapIndexed { idx, column -> column.id to idx }.toMap()

    fun evaluate(formulaRaw: String): FormulaEvaluationResult {
        val formula = formulaRaw.trim()
        val expression = formula.removePrefix("=").trim()
        if (expression.isBlank()) {
            return FormulaEvaluationResult(value = "", dependencies = emptyList())
        }

        return try {
            evaluateExpression(expression)
        } catch (error: Exception) {
            FormulaEvaluationResult(
                value = "#ERROR",
                dependencies = emptyList()
            )
        }
    }

    private fun evaluateExpression(expression: String): FormulaEvaluationResult {
        val callMatch = FUNCTION_REGEX.matchEntire(expression)
        if (callMatch != null) {
            val functionName = callMatch.groupValues[1].uppercase()
            val args = splitArgs(callMatch.groupValues[2])
            return when (functionName) {
                "SUM" -> evaluateSum(args)
                "AVG", "AVERAGE" -> evaluateAverage(args)
                "MIN" -> evaluateMin(args)
                "MAX" -> evaluateMax(args)
                "COUNT" -> evaluateCount(args)
                "COUNTIF" -> evaluateCountIf(args)
                "VLOOKUP" -> evaluateVLookup(args)
                "CONCAT", "CONCATENATE" -> evaluateConcat(args)
                else -> FormulaEvaluationResult("#ERROR", emptyList())
            }
        }

        val refs = resolveReference(expression)
        if (refs.isNotEmpty()) {
            val first = refs.first()
            return FormulaEvaluationResult(
                value = getCellDisplayValue(first.rowId, first.columnId),
                dependencies = refs
            )
        }

        val numericLiteral = parseNumber(expression)
        if (numericLiteral != null) {
            return FormulaEvaluationResult(formatNumber(numericLiteral), emptyList())
        }

        val literal = stripOuterQuotes(expression)
        return FormulaEvaluationResult(literal, emptyList())
    }

    private fun evaluateSum(args: List<String>): FormulaEvaluationResult {
        if (args.isEmpty()) return FormulaEvaluationResult("0", emptyList())

        var sum = 0.0
        val dependencies = linkedMapOf<Pair<Long, Long>, FormulaDependencyRef>()
        args.forEach { arg ->
            val refs = resolveReference(arg)
            if (refs.isNotEmpty()) {
                refs.forEach { ref ->
                    dependencies[ref.rowId to ref.columnId] = ref
                    val value = parseNumber(getCellDisplayValue(ref.rowId, ref.columnId))
                    if (value != null) sum += value
                }
            } else {
                val value = parseNumber(stripOuterQuotes(arg))
                if (value != null) sum += value
            }
        }

        return FormulaEvaluationResult(
            value = formatNumber(sum),
            dependencies = dependencies.values.toList()
        )
    }

    private fun evaluateCountIf(args: List<String>): FormulaEvaluationResult {
        if (args.size < 2) return FormulaEvaluationResult("0", emptyList())

        val rangeRefs = resolveReference(args[0])
        if (rangeRefs.isEmpty()) return FormulaEvaluationResult("0", emptyList())
        val criteria = stripOuterQuotes(args[1].trim())

        var count = 0
        rangeRefs.forEach { ref ->
            val cellValue = getCellDisplayValue(ref.rowId, ref.columnId)
            if (matchesCriteria(cellValue, criteria)) {
                count += 1
            }
        }

        return FormulaEvaluationResult(
            value = count.toString(),
            dependencies = rangeRefs
        )
    }

    private fun evaluateAverage(args: List<String>): FormulaEvaluationResult {
        val aggregate = collectNumericAggregate(args)
        val value = if (aggregate.numbers.isNotEmpty()) aggregate.numbers.average() else 0.0
        return FormulaEvaluationResult(
            value = formatNumber(value),
            dependencies = aggregate.dependencies
        )
    }

    private fun evaluateMin(args: List<String>): FormulaEvaluationResult {
        val aggregate = collectNumericAggregate(args)
        val value = aggregate.numbers.minOrNull() ?: 0.0
        return FormulaEvaluationResult(
            value = formatNumber(value),
            dependencies = aggregate.dependencies
        )
    }

    private fun evaluateMax(args: List<String>): FormulaEvaluationResult {
        val aggregate = collectNumericAggregate(args)
        val value = aggregate.numbers.maxOrNull() ?: 0.0
        return FormulaEvaluationResult(
            value = formatNumber(value),
            dependencies = aggregate.dependencies
        )
    }

    private fun evaluateCount(args: List<String>): FormulaEvaluationResult {
        if (args.isEmpty()) return FormulaEvaluationResult("0", emptyList())

        val dependencies = linkedMapOf<Pair<Long, Long>, FormulaDependencyRef>()
        var count = 0
        args.forEach { arg ->
            val refs = resolveReference(arg)
            if (refs.isNotEmpty()) {
                refs.forEach { ref ->
                    dependencies[ref.rowId to ref.columnId] = ref
                    val value = getCellDisplayValue(ref.rowId, ref.columnId)
                    if (value.trim().isNotEmpty()) {
                        count += 1
                    }
                }
            } else {
                val literal = stripOuterQuotes(arg)
                if (literal.trim().isNotEmpty()) {
                    count += 1
                }
            }
        }

        return FormulaEvaluationResult(
            value = count.toString(),
            dependencies = dependencies.values.toList()
        )
    }

    private fun evaluateConcat(args: List<String>): FormulaEvaluationResult {
        if (args.isEmpty()) return FormulaEvaluationResult("", emptyList())

        val dependencies = linkedMapOf<Pair<Long, Long>, FormulaDependencyRef>()
        val builder = StringBuilder()

        args.forEach { arg ->
            val refs = resolveReference(arg)
            if (refs.isNotEmpty()) {
                refs.forEach { ref ->
                    dependencies[ref.rowId to ref.columnId] = ref
                    builder.append(getCellDisplayValue(ref.rowId, ref.columnId))
                }
            } else {
                builder.append(stripOuterQuotes(arg))
            }
        }

        return FormulaEvaluationResult(
            value = builder.toString(),
            dependencies = dependencies.values.toList()
        )
    }

    private fun collectNumericAggregate(args: List<String>): NumericAggregate {
        if (args.isEmpty()) return NumericAggregate(emptyList(), emptyList())
        val dependencies = linkedMapOf<Pair<Long, Long>, FormulaDependencyRef>()
        val numbers = mutableListOf<Double>()

        args.forEach { arg ->
            val refs = resolveReference(arg)
            if (refs.isNotEmpty()) {
                refs.forEach { ref ->
                    dependencies[ref.rowId to ref.columnId] = ref
                    val value = parseNumber(getCellDisplayValue(ref.rowId, ref.columnId))
                    if (value != null) numbers += value
                }
            } else {
                val value = parseNumber(stripOuterQuotes(arg))
                if (value != null) numbers += value
            }
        }

        return NumericAggregate(
            numbers = numbers,
            dependencies = dependencies.values.toList()
        )
    }

    private fun evaluateVLookup(args: List<String>): FormulaEvaluationResult {
        if (args.size < 3) return FormulaEvaluationResult("#ERROR", emptyList())

        val lookupToken = args[0]
        val rangeToken = args[1]
        val targetColumnIndex = stripOuterQuotes(args[2]).toIntOrNull() ?: 1
        if (targetColumnIndex <= 0) return FormulaEvaluationResult("#ERROR", emptyList())

        val lookupRefs = resolveReference(lookupToken)
        val lookupValue =
            if (lookupRefs.isNotEmpty()) {
                val ref = lookupRefs.first()
                getCellDisplayValue(ref.rowId, ref.columnId)
            } else {
                stripOuterQuotes(lookupToken)
            }

        val matrix = resolveRangeMatrix(rangeToken)
        if (matrix.isEmpty()) return FormulaEvaluationResult("", lookupRefs)
        val width = matrix.firstOrNull()?.size ?: 0
        if (width == 0 || targetColumnIndex > width) {
            return FormulaEvaluationResult("#N/A", lookupRefs + matrix.flatten())
        }

        matrix.forEach { rowRefs ->
            val firstValue = rowRefs.firstOrNull()?.let { getCellDisplayValue(it.rowId, it.columnId) }.orEmpty()
            if (isLookupMatch(firstValue, lookupValue)) {
                val target = rowRefs[targetColumnIndex - 1]
                return FormulaEvaluationResult(
                    value = getCellDisplayValue(target.rowId, target.columnId),
                    dependencies = (lookupRefs + matrix.flatten()).distinctBy { it.rowId to it.columnId }
                )
            }
        }

        return FormulaEvaluationResult(
            value = "",
            dependencies = (lookupRefs + matrix.flatten()).distinctBy { it.rowId to it.columnId }
        )
    }

    private fun resolveRangeMatrix(tokenRaw: String): List<List<FormulaDependencyRef>> {
        val token = stripSheetPrefix(stripOuterQuotes(tokenRaw.trim()))
        val addressRange = ADDRESS_RANGE_REGEX.matchEntire(token)
        if (addressRange != null) {
            return buildAddressRangeMatrix(
                addressRange.groupValues[1],
                addressRange.groupValues[2]
            )
        }

        val columnRange = COLUMN_RANGE_REGEX.matchEntire(token)
        if (columnRange != null) {
            return buildColumnRangeMatrix(
                columnRange.groupValues[1],
                columnRange.groupValues[2]
            )
        }

        val single = parseSingleAddress(token) ?: return emptyList()
        val rowId = rowIdByOneBasedIndex[single.rowIndex] ?: return emptyList()
        val columnId = columnIdByZeroBasedIndex[single.columnIndex] ?: return emptyList()
        return listOf(
            listOf(
                FormulaDependencyRef(
                    rowId = rowId,
                    columnId = columnId,
                    address = toAddress(rowId, columnId)
                )
            )
        )
    }

    private fun resolveReference(tokenRaw: String): List<FormulaDependencyRef> {
        val token = stripSheetPrefix(stripOuterQuotes(tokenRaw.trim()))
        ADDRESS_RANGE_REGEX.matchEntire(token)?.let { match ->
            return buildAddressRangeMatrix(
                match.groupValues[1],
                match.groupValues[2]
            ).flatten()
        }
        COLUMN_RANGE_REGEX.matchEntire(token)?.let { match ->
            return buildColumnRangeMatrix(
                match.groupValues[1],
                match.groupValues[2]
            ).flatten()
        }

        val single = parseSingleAddress(token) ?: return emptyList()
        val rowId = rowIdByOneBasedIndex[single.rowIndex] ?: return emptyList()
        val columnId = columnIdByZeroBasedIndex[single.columnIndex] ?: return emptyList()

        return listOf(
            FormulaDependencyRef(
                rowId = rowId,
                columnId = columnId,
                address = toAddress(rowId, columnId)
            )
        )
    }

    private fun buildAddressRangeMatrix(startAddress: String, endAddress: String): List<List<FormulaDependencyRef>> {
        val start = parseSingleAddress(startAddress)
        val end = parseSingleAddress(endAddress)
        if (start == null || end == null) return emptyList()

        val rowStart = minOf(start.rowIndex, end.rowIndex)
        val rowEnd = maxOf(start.rowIndex, end.rowIndex)
        val colStart = minOf(start.columnIndex, end.columnIndex)
        val colEnd = maxOf(start.columnIndex, end.columnIndex)

        val matrix = mutableListOf<List<FormulaDependencyRef>>()
        for (rowIndex in rowStart..rowEnd) {
            val row = mutableListOf<FormulaDependencyRef>()
            for (columnIndex in colStart..colEnd) {
                val rowId = rowIdByOneBasedIndex[rowIndex] ?: continue
                val columnId = columnIdByZeroBasedIndex[columnIndex] ?: continue
                row += FormulaDependencyRef(
                    rowId = rowId,
                    columnId = columnId,
                    address = toAddress(rowId, columnId)
                )
            }
            if (row.isNotEmpty()) matrix += row
        }
        return matrix
    }

    private fun buildColumnRangeMatrix(startColumn: String, endColumn: String): List<List<FormulaDependencyRef>> {
        val startCol = columnNameToIndex(startColumn)
        val endCol = columnNameToIndex(endColumn)
        if (startCol < 0 || endCol < 0) return emptyList()

        val colStart = minOf(startCol, endCol)
        val colEnd = maxOf(startCol, endCol)

        val matrix = mutableListOf<List<FormulaDependencyRef>>()
        sortedRows.forEachIndexed { idx, row ->
            val rowIndex = idx + 1
            val rowRefs = mutableListOf<FormulaDependencyRef>()
            for (columnIndex in colStart..colEnd) {
                val columnId = columnIdByZeroBasedIndex[columnIndex] ?: continue
                rowRefs += FormulaDependencyRef(
                    rowId = row.id,
                    columnId = columnId,
                    address = "${columnIndexToName(columnIndex)}$rowIndex"
                )
            }
            if (rowRefs.isNotEmpty()) matrix += rowRefs
        }
        return matrix
    }

    private fun parseSingleAddress(token: String): ParsedAddress? {
        val match = SINGLE_ADDRESS_REGEX.matchEntire(token.trim()) ?: return null
        val col = columnNameToIndex(match.groupValues[1])
        val row = match.groupValues[2].toIntOrNull() ?: return null
        if (row <= 0 || col < 0) return null
        return ParsedAddress(rowIndex = row, columnIndex = col)
    }

    private fun getCellDisplayValue(rowId: Long, columnId: Long): String {
        val cell = cellsByCoordinate[rowId to columnId] ?: return ""
        return when {
            !cell.cachedValue.isNullOrBlank() -> cell.cachedValue
            else -> cell.value
        }
    }

    private fun splitArgs(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()

        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var inSingleQuotes = false
        var inDoubleQuotes = false

        raw.forEach { char ->
            when (char) {
                '\'' -> {
                    if (!inDoubleQuotes) inSingleQuotes = !inSingleQuotes
                    current.append(char)
                }

                '"' -> {
                    if (!inSingleQuotes) inDoubleQuotes = !inDoubleQuotes
                    current.append(char)
                }

                '(' -> {
                    depth += 1
                    current.append(char)
                }

                ')' -> {
                    if (depth > 0) depth -= 1
                    current.append(char)
                }

                ',' -> {
                    if (!inSingleQuotes && !inDoubleQuotes && depth == 0) {
                        result += current.toString().trim()
                        current.clear()
                    } else {
                        current.append(char)
                    }
                }

                else -> current.append(char)
            }
        }

        if (current.isNotBlank()) {
            result += current.toString().trim()
        }
        return result
    }

    private fun matchesCriteria(cellValueRaw: String, criteriaRaw: String): Boolean {
        val cellValue = cellValueRaw.trim()
        val criteria = criteriaRaw.trim()
        if (criteria.isBlank()) return cellValue.isBlank()

        val operators = listOf(">=", "<=", "<>", ">", "<", "=")
        val operator = operators.firstOrNull { criteria.startsWith(it) } ?: "="
        val rhs = criteria.removePrefix(operator).trim()

        val leftNumber = parseNumber(cellValue)
        val rightNumber = parseNumber(rhs)
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
            "<>" -> !cellValue.equals(rhs, ignoreCase = true)
            else -> cellValue.equals(rhs, ignoreCase = true)
        }
    }

    private fun isLookupMatch(left: String, right: String): Boolean {
        val leftNumber = parseNumber(left)
        val rightNumber = parseNumber(right)
        if (leftNumber != null && rightNumber != null) {
            return abs(leftNumber - rightNumber) < 0.0000001
        }
        return left.trim().equals(right.trim(), ignoreCase = true)
    }

    private fun parseNumber(raw: String): Double? {
        val cleaned =
            raw.trim()
                .replace(",", "")
                .replace(Regex("[^0-9+\\-.]"), "")
        if (cleaned.isBlank()) return null
        return cleaned.toDoubleOrNull()
    }

    private fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "#ERROR"
        if (abs(value - value.toLong().toDouble()) < 0.0000001) {
            return value.toLong().toString()
        }
        return value.toString().trimEnd('0').trimEnd('.')
    }

    private fun stripOuterQuotes(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length < 2) return trimmed
        return if (
            (trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))
        ) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    private fun stripSheetPrefix(value: String): String {
        val cleaned = value.trim()
        val bangIndex = cleaned.lastIndexOf('!')
        return if (bangIndex >= 0 && bangIndex < cleaned.length - 1) {
            cleaned.substring(bangIndex + 1)
        } else {
            cleaned
        }
    }

    private fun toAddress(rowId: Long, columnId: Long): String {
        val rowIndex = rowOneBasedIndexById[rowId] ?: 1
        val colIndex = columnZeroBasedIndexById[columnId] ?: 0
        return "${columnIndexToName(colIndex)}$rowIndex"
    }

    private fun columnNameToIndex(name: String): Int {
        var index = 0
        val upper = name.trim().uppercase()
        if (upper.isBlank()) return -1
        upper.forEach { char ->
            if (char !in 'A'..'Z') return -1
            index = index * 26 + (char.code - 'A'.code + 1)
        }
        return index - 1
    }

    private fun columnIndexToName(index: Int): String {
        var value = index
        val builder = StringBuilder()
        while (value >= 0) {
            builder.append(('A'.code + (value % 26)).toChar())
            value = (value / 26) - 1
        }
        return builder.reverse().toString()
    }

    private data class ParsedAddress(
        val rowIndex: Int,
        val columnIndex: Int
    )

    private data class NumericAggregate(
        val numbers: List<Double>,
        val dependencies: List<FormulaDependencyRef>
    )

    private companion object {
        val FUNCTION_REGEX = Regex("^([A-Za-z_][A-Za-z0-9_]*)\\((.*)\\)$")
        val SINGLE_ADDRESS_REGEX = Regex("^([A-Za-z]+)(\\d+)$")
        val ADDRESS_RANGE_REGEX = Regex("^([A-Za-z]+\\d+):([A-Za-z]+\\d+)$")
        val COLUMN_RANGE_REGEX = Regex("^([A-Za-z]+):([A-Za-z]+)$")
    }
}
