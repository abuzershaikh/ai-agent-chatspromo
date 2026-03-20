package com.message.bulksend.tablesheet.data.dao

import androidx.room.*
import com.message.bulksend.tablesheet.data.models.CellModel
import kotlinx.coroutines.flow.Flow

@Dao
interface CellDao {
    @Query("SELECT * FROM cells WHERE rowId = :rowId")
    fun getCellsByRowId(rowId: Long): Flow<List<CellModel>>
    
    @Query("SELECT * FROM cells WHERE rowId = :rowId")
    suspend fun getCellsByRowIdSync(rowId: Long): List<CellModel>
    
    @Query("SELECT * FROM cells WHERE rowId = :rowId AND columnId = :columnId LIMIT 1")
    suspend fun getCell(rowId: Long, columnId: Long): CellModel?
    
    @Query("SELECT * FROM cells WHERE rowId IN (:rowIds)")
    suspend fun getCellsByRowIds(rowIds: List<Long>): List<CellModel>

    @Query("SELECT rowId FROM cells WHERE value LIKE '%' || :query || '%'")
    suspend fun findRowIdsByValue(query: String): List<Long>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCell(cell: CellModel): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCells(cells: List<CellModel>)
    
    @Update
    suspend fun updateCell(cell: CellModel)
    
    @Query("UPDATE cells SET value = :value WHERE rowId = :rowId AND columnId = :columnId")
    suspend fun updateCellValue(rowId: Long, columnId: Long, value: String)
    
    @Delete
    suspend fun deleteCell(cell: CellModel)
    
    @Query("DELETE FROM cells WHERE rowId = :rowId")
    suspend fun deleteCellsByRowId(rowId: Long)
    
    @Query("SELECT * FROM cells WHERE columnId = :columnId AND value = :value")
    suspend fun findCellsByColumnAndValue(columnId: Long, value: String): List<CellModel>
    
    @Query("SELECT * FROM cells WHERE rowId = :rowId AND columnId = :columnId LIMIT 1")
    fun getCellSync(rowId: Long, columnId: Long): CellModel?
}
