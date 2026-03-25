package com.message.bulksend.tablesheet.data.formula

import com.message.bulksend.tablesheet.data.models.CellModel
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.RowModel
import org.junit.Assert.assertEquals
import org.junit.Test

class FormulaEngineTest {
    private val rows =
        listOf(
            RowModel(id = 1, tableId = 1, orderIndex = 0),
            RowModel(id = 2, tableId = 1, orderIndex = 1),
            RowModel(id = 3, tableId = 1, orderIndex = 2)
        )

    private val columns =
        listOf(
            ColumnModel(id = 10, tableId = 1, name = "A", orderIndex = 0),
            ColumnModel(id = 11, tableId = 1, name = "B", orderIndex = 1),
            ColumnModel(id = 12, tableId = 1, name = "C", orderIndex = 2)
        )

    private val cells =
        listOf(
            CellModel(id = 100, rowId = 1, columnId = 10, value = "10"),
            CellModel(id = 101, rowId = 2, columnId = 10, value = "20"),
            CellModel(id = 102, rowId = 3, columnId = 10, value = "30"),
            CellModel(id = 103, rowId = 1, columnId = 11, value = "PAID"),
            CellModel(id = 104, rowId = 2, columnId = 11, value = "PENDING"),
            CellModel(id = 105, rowId = 3, columnId = 11, value = "PAID"),
            CellModel(id = 106, rowId = 1, columnId = 12, value = "Alice"),
            CellModel(id = 107, rowId = 2, columnId = 12, value = "Bob"),
            CellModel(id = 108, rowId = 3, columnId = 12, value = "Cara")
        ).associateBy { it.rowId to it.columnId }

    @Test
    fun sumRange_returnsExpectedTotal() {
        val engine = FormulaEngine(rows, columns, cells)
        val result = engine.evaluate("=SUM(A1:A3)")
        assertEquals("60", result.value)
    }

    @Test
    fun countIf_matchesCriteria() {
        val engine = FormulaEngine(rows, columns, cells)
        val result = engine.evaluate("=COUNTIF(B1:B3, \"=PAID\")")
        assertEquals("2", result.value)
    }

    @Test
    fun vlookup_returnsTargetColumnValue() {
        val engine = FormulaEngine(rows, columns, cells)
        val result = engine.evaluate("=VLOOKUP(\"20\", A1:C3, 3)")
        assertEquals("Bob", result.value)
    }

    @Test
    fun averageMinMaxCount_workOnRange() {
        val engine = FormulaEngine(rows, columns, cells)
        assertEquals("20", engine.evaluate("=AVG(A1:A3)").value)
        assertEquals("10", engine.evaluate("=MIN(A1:A3)").value)
        assertEquals("30", engine.evaluate("=MAX(A1:A3)").value)
        assertEquals("3", engine.evaluate("=COUNT(A1:A3)").value)
    }
}
