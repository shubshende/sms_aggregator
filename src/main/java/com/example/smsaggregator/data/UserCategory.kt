package com.example.smsaggregator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_categories")
data class UserCategory(
    @PrimaryKey val name: String,
    val colorHex: String,
    val iconName: String, // Icon name from Material Icons
    val isCustom: Boolean = true
)
