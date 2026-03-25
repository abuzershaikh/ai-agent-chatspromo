package com.message.bulksend.tablesheet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.message.bulksend.tablesheet.data.dao.*
import com.message.bulksend.tablesheet.data.models.*

@Database(
    entities = [
        TableModel::class,
        ColumnModel::class,
        RowModel::class,
        CellModel::class,
        FolderModel::class,
        FormulaDependencyModel::class,
        CellSearchIndexModel::class,
        SheetTransactionModel::class,
        RowVersionModel::class,
        FilterViewModel::class,
        ConditionalFormatRuleModel::class
    ],
    version = 6,
    exportSchema = false
)
abstract class TableSheetDatabase : RoomDatabase() {
    
    abstract fun tableDao(): TableDao
    abstract fun columnDao(): ColumnDao
    abstract fun rowDao(): RowDao
    abstract fun cellDao(): CellDao
    abstract fun folderDao(): FolderDao
    abstract fun formulaDependencyDao(): FormulaDependencyDao
    abstract fun cellSearchIndexDao(): CellSearchIndexDao
    abstract fun sheetTransactionDao(): SheetTransactionDao
    abstract fun rowVersionDao(): RowVersionDao
    abstract fun filterViewDao(): FilterViewDao
    abstract fun conditionalFormatRuleDao(): ConditionalFormatRuleDao
    
    companion object {
        @Volatile
        private var INSTANCE: TableSheetDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tables ADD COLUMN tags TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE tables ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tables ADD COLUMN folderId INTEGER DEFAULT NULL")
            }
        }
        
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        colorHex TEXT NOT NULL DEFAULT '#1976D2'
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cells ADD COLUMN formula TEXT")
                database.execSQL("ALTER TABLE cells ADD COLUMN cachedValue TEXT")
                database.execSQL("ALTER TABLE cells ADD COLUMN numberValue REAL")
                database.execSQL("ALTER TABLE cells ADD COLUMN booleanValue INTEGER")
                database.execSQL("ALTER TABLE cells ADD COLUMN dateValue INTEGER")
                database.execSQL("ALTER TABLE cells ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL(
                    "UPDATE cells SET updatedAt = CAST(strftime('%s','now') AS INTEGER) * 1000 WHERE updatedAt = 0"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_cells_numberValue ON cells(numberValue)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_cells_booleanValue ON cells(booleanValue)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_cells_dateValue ON cells(dateValue)")

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS formula_dependencies (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        tableId INTEGER NOT NULL,
                        dependentCellId INTEGER NOT NULL,
                        sourceRowId INTEGER NOT NULL,
                        sourceColumnId INTEGER NOT NULL,
                        sourceAddress TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(dependentCellId) REFERENCES cells(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_formula_dependencies_tableId ON formula_dependencies(tableId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_formula_dependencies_dependentCellId ON formula_dependencies(dependentCellId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_formula_dependencies_source ON formula_dependencies(sourceRowId, sourceColumnId)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tables ADD COLUMN frozenColumnCount INTEGER NOT NULL DEFAULT 0")

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cell_search_index (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        tableId INTEGER NOT NULL,
                        rowId INTEGER NOT NULL,
                        columnId INTEGER NOT NULL,
                        normalizedValue TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_cell_search_index_row_column ON cell_search_index(rowId, columnId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cell_search_index_tableId ON cell_search_index(tableId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cell_search_index_normalizedValue ON cell_search_index(normalizedValue)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cell_search_index_table_value ON cell_search_index(tableId, normalizedValue)"
                )
                database.execSQL(
                    """
                    INSERT INTO cell_search_index(tableId, rowId, columnId, normalizedValue, updatedAt)
                    SELECT r.tableId, c.rowId, c.columnId, LOWER(TRIM(c.value)), CAST(strftime('%s','now') AS INTEGER) * 1000
                    FROM cells c
                    INNER JOIN rows r ON r.id = c.rowId
                    WHERE TRIM(c.value) != ''
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sheet_transactions (
                        transactionId TEXT PRIMARY KEY NOT NULL,
                        tableId INTEGER NOT NULL,
                        action TEXT NOT NULL,
                        status TEXT NOT NULL,
                        metadata TEXT,
                        createdAt INTEGER NOT NULL,
                        completedAt INTEGER
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sheet_transactions_tableId ON sheet_transactions(tableId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sheet_transactions_status ON sheet_transactions(status)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sheet_transactions_createdAt ON sheet_transactions(createdAt)"
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS row_versions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        transactionId TEXT NOT NULL,
                        tableId INTEGER NOT NULL,
                        rowId INTEGER NOT NULL,
                        columnId INTEGER NOT NULL,
                        previousValue TEXT NOT NULL,
                        newValue TEXT NOT NULL,
                        source TEXT NOT NULL,
                        changedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_row_versions_transactionId ON row_versions(transactionId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_row_versions_tableId ON row_versions(tableId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_row_versions_rowId ON row_versions(rowId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_row_versions_row_column ON row_versions(rowId, columnId)")

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS filter_views (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        tableId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        filtersJson TEXT NOT NULL,
                        sortColumnId INTEGER,
                        sortDirection TEXT NOT NULL,
                        isDefault INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(tableId) REFERENCES tables(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_filter_views_tableId ON filter_views(tableId)")

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS conditional_format_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        tableId INTEGER NOT NULL,
                        columnId INTEGER NOT NULL,
                        ruleType TEXT NOT NULL,
                        criteria TEXT NOT NULL,
                        backgroundColor TEXT,
                        textColor TEXT,
                        priority INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(tableId) REFERENCES tables(id) ON DELETE CASCADE,
                        FOREIGN KEY(columnId) REFERENCES columns(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_conditional_format_rules_tableId ON conditional_format_rules(tableId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_conditional_format_rules_columnId ON conditional_format_rules(columnId)"
                )
            }
        }
        
        fun getDatabase(context: Context): TableSheetDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TableSheetDatabase::class.java,
                    "tablesheet_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
