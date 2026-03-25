package com.message.bulksend.tablesheet.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.message.bulksend.tablesheet.data.models.RowVersionModel
import kotlinx.coroutines.flow.Flow

@Dao
interface RowVersionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: RowVersionModel)

    @Query("SELECT * FROM row_versions WHERE rowId = :rowId ORDER BY changedAt DESC")
    fun getVersionsByRowId(rowId: Long): Flow<List<RowVersionModel>>

    @Query("SELECT * FROM row_versions WHERE transactionId = :transactionId ORDER BY id ASC")
    suspend fun getVersionsByTransaction(transactionId: String): List<RowVersionModel>
}
