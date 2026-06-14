package com.example.smsaggregator.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Transaction::class, MerchantOverride::class, Budget::class, UserCategory::class, TransactionSplit::class, Subscription::class, CreditCardBill::class], version = 10, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun merchantOverrideDao(): MerchantOverrideDao
    abstract fun budgetDao(): BudgetDao
    abstract fun userCategoryDao(): UserCategoryDao
    abstract fun transactionSplitDao(): TransactionSplitDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun creditCardBillDao(): CreditCardBillDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN isIgnored INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Adds isTransfer: marks self-transfers and credit-card bill payments so they're
        // excluded from both spend and income (prevents double-counting).
        private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN isTransfer INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Adds updatedAt for last-write-wins cloud sync conflict resolution.
        private val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "transaction_database"
                ).addMigrations(MIGRATION_4_5, MIGRATION_8_9, MIGRATION_9_10).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
