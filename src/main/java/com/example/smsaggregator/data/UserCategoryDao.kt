package com.example.smsaggregator.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCategoryDao {
    @Query("SELECT * FROM user_categories")
    fun getAllCategories(): Flow<List<UserCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: UserCategory)

    @Delete
    suspend fun deleteCategory(category: UserCategory)

    @Query("SELECT * FROM user_categories WHERE name = :name")
    suspend fun getCategoryByName(name: String): UserCategory?
}
