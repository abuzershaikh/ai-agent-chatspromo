package com.message.bulksend.tablesheet.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.message.bulksend.tablesheet.data.models.FilterViewModel
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterViewDao {
    @Query("SELECT * FROM filter_views WHERE tableId = :tableId ORDER BY isDefault DESC, updatedAt DESC")
    fun getFilterViewsByTableId(tableId: Long): Flow<List<FilterViewModel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilterView(view: FilterViewModel): Long

    @Update
    suspend fun updateFilterView(view: FilterViewModel)

    @Query("DELETE FROM filter_views WHERE id = :viewId")
    suspend fun deleteFilterView(viewId: Long)

    @Query("UPDATE filter_views SET isDefault = 0 WHERE tableId = :tableId")
    suspend fun clearDefaultForTable(tableId: Long)
}
