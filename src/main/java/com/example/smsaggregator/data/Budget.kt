package com.example.smsaggregator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val category: String,
    val monthlyLimit: Double,
    val rolloverAmount: Double = 0.0, // Amount carried over from last month
    val lastRolloverMonth: Int = -1, // Calendar.MONTH
    val lastRolloverYear: Int = -1   // Calendar.YEAR
)
