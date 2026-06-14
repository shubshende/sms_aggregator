package com.example.smsaggregator.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Lightweight projection for SQL category aggregates. */
data class CategoryTotal(val category: String, val total: Double)

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<Transaction>)

    @androidx.room.Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @androidx.room.Delete
    suspend fun deleteTransactions(transactions: List<Transaction>)

    @androidx.room.Update
    suspend fun updateTransactions(transactions: List<Transaction>)

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun getAllTransactionsList(): List<Transaction>

    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int): Flow<List<Transaction>>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'DEBIT' AND date >= :startDate")
    fun getDebitSumSince(startDate: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'DEBIT' AND category = :category AND date >= :startDate")
    fun getCategorySumSince(category: String, startDate: Long): Flow<Double?>

    @Query("UPDATE transactions SET category = :newCategory, updatedAt = :now WHERE id = :transactionId")
    suspend fun updateCategory(transactionId: Long, newCategory: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET category = :newCategory, updatedAt = :now WHERE LOWER(merchant) = LOWER(:merchantLower)")
    suspend fun updateCategoryByMerchantCaseInsensitive(merchantLower: String, newCategory: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET merchant = :newMerchant, category = :newCategory, updatedAt = :now WHERE (merchant = 'Unknown' OR merchant = :oldMerchant) AND rawSms = :smsContent")
    suspend fun updateMerchantAndCategoryBySms(oldMerchant: String, newMerchant: String, newCategory: String, smsContent: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET category = :newCategory, updatedAt = :now WHERE category = :oldCategory")
    suspend fun updateCategoryName(oldCategory: String, newCategory: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET isIgnored = :ignored, updatedAt = :now WHERE id = :transactionId")
    suspend fun updateIgnored(transactionId: Long, ignored: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET isTransfer = :isTransfer, updatedAt = :now WHERE id = :transactionId")
    suspend fun updateTransfer(transactionId: Long, isTransfer: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET isIgnored = :ignored, updatedAt = :now WHERE id IN (:ids)")
    suspend fun updateIgnoredForIds(ids: List<Long>, ignored: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET category = :category, updatedAt = :now WHERE id IN (:ids)")
    suspend fun updateCategoryForIds(ids: List<Long>, category: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM transactions WHERE date = :date AND amount = :amount AND merchant = :merchant LIMIT 1")
    suspend fun findByNaturalKey(date: Long, amount: Double, merchant: String): Transaction?

    // ───────── SQL aggregates (gross debits, excluding ignored & transfers) ─────────
    // Note: these don't net refunds or apply transaction splits (splits live in a separate
    // table and refunds are classified by SMS text), so the split/refund-aware screens still
    // aggregate in Kotlin. These are for quick totals (e.g. budget-alert checks).

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'DEBIT' AND isIgnored = 0 AND isTransfer = 0 AND date >= :start AND date < :end")
    suspend fun getSpendBetween(start: Long, end: Long): Double

    @Query("SELECT category AS category, COALESCE(SUM(amount), 0) AS total FROM transactions WHERE type = 'DEBIT' AND isIgnored = 0 AND isTransfer = 0 AND date >= :start AND date < :end GROUP BY category ORDER BY total DESC")
    suspend fun getCategorySpendBetween(start: Long, end: Long): List<CategoryTotal>
}
