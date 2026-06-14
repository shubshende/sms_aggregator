package com.example.smsaggregator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions", indices = [androidx.room.Index(value = ["date", "amount", "merchant"], unique = true)])
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val category: String,
    val date: Long,
    val type: TransactionType,
    val source: String, // e.g. "UPI", "Credit Card", etc.
    val rawSms: String = "", // Original SMS body for verification
    val isIgnored: Boolean = false, // If true, excluded from totals
    val isManual: Boolean = false, // If true, manually entered
    val receiptPath: String? = null, // Path to attached receipt image
    val isTransfer: Boolean = false, // If true, money moved between own accounts / credit-card bill payment — never counted as spend OR income
    val updatedAt: Long = 0L // Last local mutation time; used for last-write-wins cloud merge
)

enum class TransactionType {
    DEBIT, CREDIT
}

/**
 * Single source of truth for money-flow classification, shared by the SMS parser
 * (which decides a row's category) and the spending aggregations (which decide what
 * counts as spend vs income). Keeping the refund definition here prevents the parser
 * and the UI from drifting apart.
 */
object MoneyFlow {
    /** Credits that "undo" a previous purchase rather than being fresh income. */
    val REFUND_REGEX = Regex(
        "refund|reversal|reversed|charge\\s*back|chargeback|cashback|cash\\s*back",
        RegexOption.IGNORE_CASE
    )

    fun isRefundText(text: String): Boolean = REFUND_REGEX.containsMatchIn(text)
}

/** Money-out rows that count as spending (excludes ignored rows and transfers). */
val Transaction.isExpense: Boolean
    get() = type == TransactionType.DEBIT && !isIgnored && !isTransfer

/** A credit that offsets a previous purchase (refund / reversal / cashback). */
val Transaction.isRefund: Boolean
    get() = type == TransactionType.CREDIT && !isIgnored && !isTransfer && MoneyFlow.isRefundText(rawSms)

/** A credit that represents real money coming in (salary, interest, received transfer). */
val Transaction.isIncome: Boolean
    get() = type == TransactionType.CREDIT && !isIgnored && !isTransfer && !MoneyFlow.isRefundText(rawSms)
