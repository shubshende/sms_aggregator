package com.example.smsaggregator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credit_card_bills")
data class CreditCardBill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankName: String,
    val cardDigits: String, // e.g., "XX4265"
    val totalDue: Double,
    val minDue: Double,
    val dueDate: Long,
    val isPaid: Boolean = false,
    val rawSms: String = "",
    val billGeneratedDate: Long = System.currentTimeMillis()
)
