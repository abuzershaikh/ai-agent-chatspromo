package com.message.bulksend.tablesheet.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.message.bulksend.tablesheet.data.models.SheetTransactionModel

@Dao
interface SheetTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: SheetTransactionModel)

    @Query(
        """
        UPDATE sheet_transactions
        SET status = :status, completedAt = :completedAt
        WHERE transactionId = :transactionId
        """
    )
    suspend fun markTransactionStatus(
        transactionId: String,
        status: String,
        completedAt: Long
    )
}
