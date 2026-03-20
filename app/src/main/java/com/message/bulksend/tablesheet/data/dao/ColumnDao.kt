package com.message.bulksend.tablesheet.data.dao

import androidx.room.*
import com.message.bulksend.tablesheet.data.models.ColumnModel
import kotlinx.coroutines.flow.Flow

@Dao
interface ColumnDao {
    @Query("SELECT * FROM columns WHERE tableId = :tableId ORDER BY orderIndex ASC")
    fun getColumnsByTableId(tableId: Long): Flow<List<ColumnModel>>
    
    @Query("SELECT * FROM columns WHERE tableId = :tableId ORDER BY orderIndex ASC")
    suspend fun getColumnsByTableIdSync(tableId: Long): List<ColumnModel>
    
    @Query("SELECT * FROM columns WHERE id = :columnId")
    suspend fun getColumnById(columnId: Long): ColumnModel?
    
    @Query("SELECT COUNT(*) FROM columns WHERE tableId = :tableId")
    suspend fun getColumnCount(tableId: Long): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertColumn(column: ColumnModel): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertColumns(columns: List<ColumnModel>)
    
    @Update
    suspend fun updateColumn(column: ColumnModel)
    
    @Query("UPDATE columns SET name = :name, type = :type, width = :width, selectOptions = :selectOptions WHERE id = :columnId")
    suspend fun updateColumnProperties(columnId: Long, name: String, type: String, width: Float, selectOptions: String?)
    
    @Delete
    suspend fun deleteColumn(column: ColumnModel)
    
    @Query("DELETE FROM columns WHERE id = :columnId")
    suspend fun deleteColumnById(columnId: Long)
    
    @Query("UPDATE columns SET orderIndex = :orderIndex WHERE id = :columnId")
    suspend fun updateColumnOrder(columnId: Long, orderIndex: Int)
    
    @Transaction
    suspend fun updateColumnsOrder(columns: List<ColumnModel>) {
        columns.forEachIndexed { index, column ->
            updateColumnOrder(column.id, index)
        }
    }
}
