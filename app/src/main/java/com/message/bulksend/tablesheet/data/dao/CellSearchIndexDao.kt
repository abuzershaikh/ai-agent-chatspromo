package com.message.bulksend.tablesheet.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.message.bulksend.tablesheet.data.models.CellSearchIndexModel

@Dao
interface CellSearchIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CellSearchIndexModel)

    @Query("DELETE FROM cell_search_index WHERE rowId = :rowId AND columnId = :columnId")
    suspend fun deleteByCell(rowId: Long, columnId: Long)

    @Query("DELETE FROM cell_search_index WHERE rowId = :rowId")
    suspend fun deleteByRowId(rowId: Long)

    @Query("DELETE FROM cell_search_index WHERE columnId = :columnId")
    suspend fun deleteByColumnId(columnId: Long)

    @Query("DELETE FROM cell_search_index WHERE tableId = :tableId")
    suspend fun deleteByTableId(tableId: Long)

    @Query(
        """
        SELECT DISTINCT rowId
        FROM cell_search_index
        WHERE tableId IN (:tableIds)
        AND normalizedValue LIKE :prefix || '%'
        LIMIT :limit
        """
    )
    suspend fun searchRowIdsByPrefix(
        tableIds: List<Long>,
        prefix: String,
        limit: Int
    ): List<Long>

    @Query(
        """
        SELECT DISTINCT rowId
        FROM cell_search_index
        WHERE tableId IN (:tableIds)
        AND normalizedValue LIKE '%' || :token || '%'
        LIMIT :limit
        """
    )
    suspend fun searchRowIdsByContains(
        tableIds: List<Long>,
        token: String,
        limit: Int
    ): List<Long>

    @Query(
        """
        SELECT DISTINCT rowId
        FROM cell_search_index
        WHERE tableId = :tableId
        AND columnId = :columnId
        AND normalizedValue = :normalizedValue
        LIMIT :limit
        """
    )
    suspend fun searchRowIdsByColumnExact(
        tableId: Long,
        columnId: Long,
        normalizedValue: String,
        limit: Int
    ): List<Long>

    @Query(
        """
        SELECT DISTINCT rowId
        FROM cell_search_index
        WHERE tableId = :tableId
        AND columnId = :columnId
        AND normalizedValue LIKE :prefix || '%'
        LIMIT :limit
        """
    )
    suspend fun searchRowIdsByColumnPrefix(
        tableId: Long,
        columnId: Long,
        prefix: String,
        limit: Int
    ): List<Long>

    @Query(
        """
        SELECT DISTINCT rowId
        FROM cell_search_index
        WHERE tableId = :tableId
        AND columnId = :columnId
        AND normalizedValue LIKE '%' || :token || '%'
        LIMIT :limit
        """
    )
    suspend fun searchRowIdsByColumnContains(
        tableId: Long,
        columnId: Long,
        token: String,
        limit: Int
    ): List<Long>
}
