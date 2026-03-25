package com.message.bulksend.tablesheet.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.message.bulksend.tablesheet.data.models.ConditionalFormatRuleModel
import kotlinx.coroutines.flow.Flow

@Dao
interface ConditionalFormatRuleDao {
    @Query(
        """
        SELECT * FROM conditional_format_rules
        WHERE tableId = :tableId
        ORDER BY priority ASC, updatedAt DESC
        """
    )
    fun getRulesByTableId(tableId: Long): Flow<List<ConditionalFormatRuleModel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: ConditionalFormatRuleModel): Long

    @Update
    suspend fun updateRule(rule: ConditionalFormatRuleModel)

    @Query("DELETE FROM conditional_format_rules WHERE id = :ruleId")
    suspend fun deleteRule(ruleId: Long)
}
