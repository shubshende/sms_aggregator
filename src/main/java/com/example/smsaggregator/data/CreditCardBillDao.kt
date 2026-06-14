package com.example.smsaggregator.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditCardBillDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: CreditCardBill)

    @Update
    suspend fun updateBill(bill: CreditCardBill)

    @Query("SELECT * FROM credit_card_bills ORDER BY dueDate ASC")
    fun getAllBills(): Flow<List<CreditCardBill>>

    @Query("SELECT * FROM credit_card_bills WHERE isPaid = 0 ORDER BY dueDate ASC")
    fun getUnpaidBills(): Flow<List<CreditCardBill>>

    @Query("SELECT * FROM credit_card_bills WHERE bankName = :bankName AND cardDigits = :cardDigits AND totalDue = :totalDue AND dueDate = :dueDate LIMIT 1")
    suspend fun findDuplicateBill(bankName: String, cardDigits: String, totalDue: Double, dueDate: Long): CreditCardBill?

    @Query("UPDATE credit_card_bills SET isPaid = 1 WHERE bankName = :bankName AND cardDigits = :cardDigits AND isPaid = 0")
    suspend fun markAsPaid(bankName: String, cardDigits: String)
    
    @Query("UPDATE credit_card_bills SET isPaid = 1 WHERE id = :billId")
    suspend fun markAsPaidById(billId: Long)
}
