package com.example.smsaggregator.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subscriptions",
    indices = [Index(value = ["merchant", "amount"], unique = true)]
)
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val amount: Double,
    val category: String,
    val frequencyDays: Int, // e.g., 30 for monthly
    val lastDate: Long,
    val nextExpectedDate: Long,
    val isActive: Boolean = true
)
