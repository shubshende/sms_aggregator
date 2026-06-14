package com.example.smsaggregator.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Handles bi-directional sync between the local Room database and Firebase Firestore.
 * 
 * Firestore Schema:
 *   users/{uid}/transactions/{docId}  — mirrors the Transaction entity
 *   users/{uid}/merchant_overrides/{merchantKey} — mirrors MerchantOverride entity
 */
class FirestoreSync(
    private val transactionDao: TransactionDao,
    private val overrideDao: MerchantOverrideDao
) {
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreSync"
    }

    // ───────── UPLOAD (Local → Cloud) ─────────

    /**
     * Uploads all local transactions to Firestore.
     * Uses batched writes for efficiency (max 500 per batch).
     */
    suspend fun uploadAllTransactions(uid: String) {
        try {
            val transactions = transactionDao.getAllTransactionsList()
            if (transactions.isEmpty()) return

            val collectionRef = firestore.collection("users").document(uid).collection("transactions")

            // Batch in groups of 400 (safe margin under 500 limit)
            transactions.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { txn ->
                    val docId = "${txn.date}_${txn.amount}_${txn.merchant.hashCode()}"
                    val docRef = collectionRef.document(docId)
                    val data = hashMapOf(
                        "amount" to txn.amount,
                        "merchant" to txn.merchant,
                        "category" to txn.category,
                        "date" to txn.date,
                        "type" to txn.type.name,
                        "source" to txn.source,
                        "rawSms" to txn.rawSms,
                        "isIgnored" to txn.isIgnored,
                        "isTransfer" to txn.isTransfer,
                        "updatedAt" to txn.updatedAt
                    )
                    batch.set(docRef, data, SetOptions.merge())
                }
                batch.commit().await()
            }
            Log.d(TAG, "Uploaded ${transactions.size} transactions to cloud")
        } catch (e: Exception) {
            Log.e(TAG, "Upload transactions failed", e)
        }
    }

    /**
     * Uploads all merchant overrides to Firestore.
     */
    suspend fun uploadAllOverrides(uid: String) {
        try {
            val overrides = overrideDao.getAllOverrides()
            if (overrides.isEmpty()) return

            val collectionRef = firestore.collection("users").document(uid).collection("merchant_overrides")

            overrides.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { ovr ->
                    val docRef = collectionRef.document(ovr.merchantKey)
                    val data = hashMapOf(
                        "category" to ovr.category,
                        "source" to ovr.source
                    )
                    batch.set(docRef, data, SetOptions.merge())
                }
                batch.commit().await()
            }
            Log.d(TAG, "Uploaded ${overrides.size} overrides to cloud")
        } catch (e: Exception) {
            Log.e(TAG, "Upload overrides failed", e)
        }
    }

    /**
     * Uploads a single transaction (called after new SMS parse or category update).
     */
    suspend fun uploadTransaction(uid: String, txn: Transaction) {
        try {
            val docId = "${txn.date}_${txn.amount}_${txn.merchant.hashCode()}"
            val docRef = firestore.collection("users").document(uid)
                .collection("transactions").document(docId)
            val data = hashMapOf(
                "amount" to txn.amount,
                "merchant" to txn.merchant,
                "category" to txn.category,
                "date" to txn.date,
                "type" to txn.type.name,
                "source" to txn.source,
                "rawSms" to txn.rawSms,
                "isIgnored" to txn.isIgnored,
                "isTransfer" to txn.isTransfer,
                "updatedAt" to txn.updatedAt
            )
            docRef.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Upload single transaction failed", e)
        }
    }

    /**
     * Deletes a collection of transactions from Firestore.
     */
    suspend fun deleteTransactions(uid: String, txns: List<Transaction>) {
        try {
            val collectionRef = firestore.collection("users").document(uid).collection("transactions")
            // Batch in groups of 400
            txns.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { txn ->
                    val docId = "${txn.date}_${txn.amount}_${txn.merchant.hashCode()}"
                    val docRef = collectionRef.document(docId)
                    batch.delete(docRef)
                }
                batch.commit().await()
            }
            Log.d(TAG, "Deleted ${txns.size} false-positive transactions from cloud")
        } catch (e: Exception) {
            Log.e(TAG, "Delete transactions failed", e)
        }
    }

    /**
     * Uploads a single merchant override.
     */
    suspend fun uploadOverride(uid: String, override: MerchantOverride) {
        try {
            val docRef = firestore.collection("users").document(uid)
                .collection("merchant_overrides").document(override.merchantKey)
            val data = hashMapOf(
                "category" to override.category,
                "source" to override.source
            )
            docRef.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Upload single override failed", e)
        }
    }

    // ───────── DOWNLOAD (Cloud → Local) ─────────

    /**
     * Downloads all transactions from Firestore and merges into local Room DB.
     * Uses IGNORE conflict strategy so existing local records aren't overwritten.
     */
    suspend fun downloadAllTransactions(uid: String): Int {
        return try {
            val snapshot = firestore.collection("users").document(uid)
                .collection("transactions").get().await()

            val cloudTransactions = snapshot.documents.mapNotNull { doc ->
                try {
                    Transaction(
                        amount = doc.getDouble("amount") ?: return@mapNotNull null,
                        merchant = doc.getString("merchant") ?: return@mapNotNull null,
                        category = doc.getString("category") ?: "Other",
                        date = doc.getLong("date") ?: return@mapNotNull null,
                        type = TransactionType.valueOf(doc.getString("type") ?: "DEBIT"),
                        source = doc.getString("source") ?: "Unknown",
                        rawSms = doc.getString("rawSms") ?: "",
                        isIgnored = doc.getBoolean("isIgnored") ?: false,
                        isTransfer = doc.getBoolean("isTransfer") ?: false,
                        updatedAt = doc.getLong("updatedAt") ?: 0L
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping malformed cloud transaction: ${doc.id}", e)
                    null
                }
            }

            // Last-write-wins merge: insert new rows, and overwrite a local row's mutable
            // fields only when the cloud copy was edited more recently (updatedAt newer).
            var changed = 0
            for (cloud in cloudTransactions) {
                val local = transactionDao.findByNaturalKey(cloud.date, cloud.amount, cloud.merchant)
                if (local == null) {
                    transactionDao.insertAll(listOf(cloud))
                    changed++
                } else if (cloud.updatedAt > local.updatedAt) {
                    transactionDao.updateTransactions(listOf(local.copy(
                        category = cloud.category,
                        isIgnored = cloud.isIgnored,
                        isTransfer = cloud.isTransfer,
                        source = cloud.source,
                        updatedAt = cloud.updatedAt
                    )))
                    changed++
                }
            }

            Log.d(TAG, "Merged $changed of ${cloudTransactions.size} cloud transactions")
            cloudTransactions.size
        } catch (e: Exception) {
            Log.e(TAG, "Download transactions failed", e)
            0
        }
    }

    /**
     * Downloads all merchant overrides from Firestore and merges into local Room DB.
     * Uses REPLACE conflict strategy so cloud overrides take precedence.
     */
    suspend fun downloadAllOverrides(uid: String): Int {
        return try {
            val snapshot = firestore.collection("users").document(uid)
                .collection("merchant_overrides").get().await()

            val cloudOverrides = snapshot.documents.mapNotNull { doc ->
                try {
                    MerchantOverride(
                        merchantKey = doc.id,
                        category = doc.getString("category") ?: return@mapNotNull null,
                        source = doc.getString("source") ?: "cloud"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping malformed cloud override: ${doc.id}", e)
                    null
                }
            }

            if (cloudOverrides.isNotEmpty()) {
                overrideDao.insertAll(cloudOverrides)
            }

            Log.d(TAG, "Downloaded ${cloudOverrides.size} overrides from cloud")
            cloudOverrides.size
        } catch (e: Exception) {
            Log.e(TAG, "Download overrides failed", e)
            0
        }
    }

    // ───────── FULL SYNC ─────────

    /**
     * Performs a full bi-directional sync:
     * 1. Download cloud data → merge into local
     * 2. Upload local data → merge into cloud
     */
    suspend fun fullSync(uid: String): SyncResult {
        val downloadedTxns = downloadAllTransactions(uid)
        val downloadedOverrides = downloadAllOverrides(uid)
        uploadAllTransactions(uid)
        uploadAllOverrides(uid)
        return SyncResult(downloadedTxns, downloadedOverrides)
    }
}

data class SyncResult(val downloadedTransactions: Int, val downloadedOverrides: Int)
