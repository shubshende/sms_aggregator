package com.example.smsaggregator.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MerchantOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverride(override: MerchantOverride)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(overrides: List<MerchantOverride>)

    @Query("SELECT category FROM merchant_overrides WHERE merchantKey = :merchantKey LIMIT 1")
    suspend fun getCategoryForMerchant(merchantKey: String): String?

    @Query("SELECT * FROM merchant_overrides")
    suspend fun getAllOverrides(): List<MerchantOverride>

    @Query("DELETE FROM merchant_overrides WHERE merchantKey = :merchantKey")
    suspend fun deleteOverride(merchantKey: String)

    @Query("DELETE FROM merchant_overrides WHERE source = :source")
    suspend fun deleteOverridesByType(source: String)

    @Query("UPDATE merchant_overrides SET category = :newCategory WHERE category = :oldCategory")
    suspend fun updateCategoryName(oldCategory: String, newCategory: String)
}
