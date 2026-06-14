package com.example.smsaggregator.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "transaction_splits",
    indices = [androidx.room.Index(value = ["transactionId"])],
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TransactionSplit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val amount: Double,
    val category: String,
    val note: String = ""
)
