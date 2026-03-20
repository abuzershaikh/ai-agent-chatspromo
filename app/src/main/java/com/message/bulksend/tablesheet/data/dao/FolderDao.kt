package com.message.bulksend.tablesheet.data.dao

import androidx.room.*
import com.message.bulksend.tablesheet.data.models.FolderModel
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    
    @Query("SELECT * FROM folders ORDER BY CASE WHEN name = 'AI Agent Data Sheet' THEN 1 ELSE 0 END DESC, updatedAt DESC")
    fun getAllFolders(): Flow<List<FolderModel>>
    
    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Long): FolderModel?
    
    @Insert
    suspend fun insertFolder(folder: FolderModel): Long
    
    @Update
    suspend fun updateFolder(folder: FolderModel)
    
    @Delete
    suspend fun deleteFolder(folder: FolderModel)
    
    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: Long)
    
    @Query("SELECT COUNT(*) FROM tables WHERE folderId = :folderId")
    suspend fun getTableCountInFolder(folderId: Long): Int

    @Query("SELECT * FROM folders WHERE name = :name LIMIT 1")
    suspend fun getFolderByName(name: String): FolderModel?
    
    @Query("SELECT * FROM folders WHERE name = :name LIMIT 1")
    fun getFolderByNameSync(name: String): FolderModel?
}