package com.example.smsaggregator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchant_overrides")
data class MerchantOverride(
    @PrimaryKey val merchantKey: String, // Lowercase trimmed merchant name
    val category: String,
    val source: String = "user" // "user" or "gemini"
)
