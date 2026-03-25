package com.message.bulksend.tablesheet.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.message.bulksend.tablesheet.data.models.FormulaDependencyModel

@Dao
interface FormulaDependencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDependencies(dependencies: List<FormulaDependencyModel>)

    @Query("DELETE FROM formula_dependencies WHERE dependentCellId = :dependentCellId")
    suspend fun deleteDependenciesForCell(dependentCellId: Long)

    @Query("DELETE FROM formula_dependencies WHERE tableId = :tableId")
    suspend fun deleteDependenciesForTable(tableId: Long)

    @Query(
        """
        SELECT DISTINCT dependentCellId
        FROM formula_dependencies
        WHERE tableId = :tableId
        AND sourceRowId = :sourceRowId
        AND sourceColumnId = :sourceColumnId
        """
    )
    suspend fun getDependentCellIdsForSource(
        tableId: Long,
        sourceRowId: Long,
        sourceColumnId: Long
    ): List<Long>

    @Query("SELECT COUNT(*) FROM formula_dependencies WHERE tableId = :tableId")
    suspend fun getDependencyCountForTable(tableId: Long): Int

    @Query("SELECT * FROM formula_dependencies WHERE tableId = :tableId")
    suspend fun getDependenciesForTable(tableId: Long): List<FormulaDependencyModel>
}
