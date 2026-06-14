package com.example.smsaggregator.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionSplitDao {
    @Query("SELECT * FROM transaction_splits WHERE transactionId = :transactionId")
    fun getSplitsForTransaction(transactionId: Long): Flow<List<TransactionSplit>>

    @Insert
    suspend fun insertSplits(splits: List<TransactionSplit>)

    @Query("DELETE FROM transaction_splits WHERE transactionId = :transactionId")
    suspend fun deleteSplitsForTransaction(transactionId: Long)

    @Query("SELECT * FROM transaction_splits")
    fun getAllSplits(): Flow<List<TransactionSplit>>
}
