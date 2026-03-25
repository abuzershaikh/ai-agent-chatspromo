package com.message.bulksend.tablesheet.data.dao

import androidx.room.*
import com.message.bulksend.tablesheet.data.models.RowModel
import kotlinx.coroutines.flow.Flow

@Dao
interface RowDao {
    @Query("SELECT * FROM rows WHERE tableId = :tableId ORDER BY orderIndex ASC")
    fun getRowsByTableId(tableId: Long): Flow<List<RowModel>>
    
    @Query("SELECT * FROM rows WHERE tableId = :tableId ORDER BY orderIndex ASC")
    suspend fun getRowsByTableIdSync(tableId: Long): List<RowModel>

    @Query("SELECT * FROM rows WHERE tableId = :tableId AND id IN (:rowIds) ORDER BY orderIndex ASC")
    suspend fun getRowsByTableIdAndIds(tableId: Long, rowIds: List<Long>): List<RowModel>
    
    @Query("SELECT * FROM rows WHERE id = :rowId")
    suspend fun getRowById(rowId: Long): RowModel?
    
    @Query("SELECT * FROM rows WHERE id = :rowId")
    fun getRowByIdSync(rowId: Long): RowModel?
    
    @Query("SELECT COUNT(*) FROM rows WHERE tableId = :tableId")
    suspend fun getRowCount(tableId: Long): Int
    
    @Query("SELECT COUNT(*) FROM rows WHERE tableId = :tableId")
    fun getRowCountSync(tableId: Long): Int
    
    @Query("SELECT MAX(orderIndex) FROM rows WHERE tableId = :tableId")
    suspend fun getMaxOrderIndex(tableId: Long): Int?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRow(row: RowModel): Long
    
    @Update
    suspend fun updateRow(row: RowModel)
    
    @Delete
    suspend fun deleteRow(row: RowModel)
    
    @Query("DELETE FROM rows WHERE id = :rowId")
    suspend fun deleteRowById(rowId: Long)
    
    @Query("DELETE FROM rows WHERE tableId = :tableId")
    suspend fun deleteRowsByTableId(tableId: Long)
}
