package com.message.bulksend.tablesheet.data.dao

import androidx.room.*
import com.message.bulksend.tablesheet.data.models.TableModel
import kotlinx.coroutines.flow.Flow

@Dao
interface TableDao {
    @Query("SELECT * FROM tables ORDER BY updatedAt DESC")
    fun getAllTables(): Flow<List<TableModel>>
    
    @Query("SELECT * FROM tables WHERE id = :tableId")
    suspend fun getTableById(tableId: Long): TableModel?

    @Query("SELECT * FROM tables WHERE name = :name LIMIT 1")
    suspend fun getTableByName(name: String): TableModel?

    @Query("SELECT * FROM tables WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getTableByNameNormalized(name: String): TableModel?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTable(table: TableModel): Long
    
    @Update
    suspend fun updateTable(table: TableModel)
    
    @Delete
    suspend fun deleteTable(table: TableModel)
    
    @Query("DELETE FROM tables WHERE id = :tableId")
    suspend fun deleteTableById(tableId: Long)
    
    @Query("UPDATE tables SET rowCount = :count, updatedAt = :time WHERE id = :tableId")
    suspend fun updateRowCount(tableId: Long, count: Int, time: Long = System.currentTimeMillis())
    
    @Query("UPDATE tables SET columnCount = :count, updatedAt = :time WHERE id = :tableId")
    suspend fun updateColumnCount(tableId: Long, count: Int, time: Long = System.currentTimeMillis())

    @Query("UPDATE tables SET frozenColumnCount = :count, updatedAt = :time WHERE id = :tableId")
    suspend fun updateFrozenColumnCount(tableId: Long, count: Int, time: Long = System.currentTimeMillis())
    
    @Query("UPDATE tables SET tags = :tags, updatedAt = :time WHERE id = :tableId")
    suspend fun updateTags(tableId: Long, tags: String?, time: Long = System.currentTimeMillis())
    
    @Query("UPDATE tables SET isFavorite = :isFavorite, updatedAt = :time WHERE id = :tableId")
    suspend fun updateFavorite(tableId: Long, isFavorite: Boolean, time: Long = System.currentTimeMillis())
    
    @Query("UPDATE tables SET name = :name, updatedAt = :time WHERE id = :tableId")
    suspend fun updateName(tableId: Long, name: String, time: Long = System.currentTimeMillis())
    
    @Query("SELECT * FROM tables WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteTables(): Flow<List<TableModel>>
    
    @Query("SELECT * FROM tables WHERE tags LIKE '%' || :tag || '%' ORDER BY updatedAt DESC")
    fun getTablesByTag(tag: String): Flow<List<TableModel>>
    
    // Folder-related methods
    @Query("UPDATE tables SET folderId = :folderId, updatedAt = :time WHERE id = :tableId")
    suspend fun updateTableFolder(tableId: Long, folderId: Long?, time: Long = System.currentTimeMillis())
    
    @Query("UPDATE tables SET folderId = NULL, updatedAt = :time WHERE folderId = :folderId")
    suspend fun moveTablesFromFolder(folderId: Long, time: Long = System.currentTimeMillis())
    
    @Query("SELECT * FROM tables WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun getTablesByFolderIdSync(folderId: Long): List<TableModel>
    
    @Query("SELECT * FROM tables WHERE id = :tableId")
    fun getTableByIdSync(tableId: Long): TableModel?

    @Query("SELECT * FROM tables ORDER BY updatedAt DESC")
    suspend fun getAllTablesSync(): List<TableModel>
    
    @Query("SELECT COUNT(*) FROM rows WHERE tableId = :tableId")
    fun getRowCountSync(tableId: Long): Int
}
