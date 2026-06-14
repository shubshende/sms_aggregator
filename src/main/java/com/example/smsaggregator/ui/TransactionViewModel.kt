package com.example.smsaggregator.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smsaggregator.data.AppDatabase
import com.example.smsaggregator.data.AuthRepository
import com.example.smsaggregator.data.Budget
import com.example.smsaggregator.data.FirestoreSync
import com.example.smsaggregator.data.MerchantOverride
import com.example.smsaggregator.data.Transaction
import com.example.smsaggregator.data.isExpense
import com.example.smsaggregator.data.isIncome
import com.example.smsaggregator.data.isRefund
import com.example.smsaggregator.logic.GeminiService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.smsaggregator.ui.CatSlice
import com.example.smsaggregator.ui.theme.CatColor

enum class AuthState { LOADING, SIGNED_IN, SIGNED_OUT }

data class UserProfile(
    val displayName: String = "User",
    val email: String = "",
    val photoUrl: String? = null
)

data class AiReportItem(val merchant: String, val category: String, val sms: String, val isSorted: Boolean)
data class AiReport(val items: List<AiReportItem>)

class TransactionViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val transactionDao = db.transactionDao()
    private val overrideDao = db.merchantOverrideDao()
    private val budgetDao = db.budgetDao()
    private val categoryDao = db.userCategoryDao()
    private val splitDao = db.transactionSplitDao()
    private val subscriptionDao = db.subscriptionDao()
    private val creditCardBillDao = db.creditCardBillDao()

    // Auth
    val authRepository = AuthRepository(application)
    private val firestoreSync = FirestoreSync(transactionDao, overrideDao)

    private val _authState = MutableStateFlow(
        if (authRepository.isSignedIn) AuthState.SIGNED_IN else AuthState.SIGNED_OUT
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    // Transactions
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _categories = MutableStateFlow<List<com.example.smsaggregator.data.UserCategory>>(emptyList())
    val categories: StateFlow<List<com.example.smsaggregator.data.UserCategory>> = _categories.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<com.example.smsaggregator.data.Subscription>>(emptyList())
    val subscriptions: StateFlow<List<com.example.smsaggregator.data.Subscription>> = _subscriptions.asStateFlow()

    private val _allSplits = MutableStateFlow<List<com.example.smsaggregator.data.TransactionSplit>>(emptyList())
    val allSplits: StateFlow<List<com.example.smsaggregator.data.TransactionSplit>> = _allSplits.asStateFlow()

    val splitsByTxn: StateFlow<Map<Long, List<com.example.smsaggregator.data.TransactionSplit>>> = _allSplits.map { splits ->
        splits.groupBy { it.transactionId }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _isClassifying = MutableStateFlow(false)
    val isClassifying: StateFlow<Boolean> = _isClassifying.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isDarkMode = MutableStateFlow(
        application.getSharedPreferences("sms_agg_prefs", Context.MODE_PRIVATE).getBoolean("dark_mode", true)
    )
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(
        application.getSharedPreferences("sms_agg_prefs", Context.MODE_PRIVATE).getBoolean("biometric_lock", false)
    )
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _classifyResult = MutableStateFlow<String?>(null)
    val classifyResult: StateFlow<String?> = _classifyResult.asStateFlow()

    // Search & Filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _quickFilter = MutableStateFlow<String?>(null)
    val quickFilter: StateFlow<String?> = _quickFilter.asStateFlow()

    private val _sourceFilter = MutableStateFlow<String?>(null)
    val sourceFilter: StateFlow<String?> = _sourceFilter.asStateFlow()

    val monthTxns: StateFlow<List<Transaction>> = combine(_transactions, _sourceFilter) { txns, selectedSource ->
        val cal = Calendar.getInstance()
        val curMonth = cal.get(Calendar.MONTH)
        val curYear = cal.get(Calendar.YEAR)
        val monthRange = com.example.smsaggregator.util.DateUtils.monthRange(curYear, curMonth)
        val source = selectedSource ?: "All"
        txns.filter {
            !it.isIgnored &&
                it.date >= monthRange.first && it.date < monthRange.second &&
                (source == "All" || it.source == source)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTxns: StateFlow<List<Transaction>> = combine(_transactions, _sourceFilter) { txns, selectedSource ->
        val source = selectedSource ?: "All"
        txns.asSequence()
            .filter { !it.isIgnored && (source == "All" || it.source == source) }
            .take(20)
            .toList()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dateRangeFilter = MutableStateFlow<Pair<Long, Long>?>(null)
    val dateRangeFilter: StateFlow<Pair<Long, Long>?> = _dateRangeFilter.asStateFlow()

    // Budgets
    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    val budgets: StateFlow<List<Budget>> = _budgets.asStateFlow()

    val ringSlices: StateFlow<List<CatSlice>> = combine(monthTxns, budgets, splitsByTxn) { mTxns, b, splitsMap ->
        val catMap = mutableMapOf<String, Double>()
        val cntMap = mutableMapOf<String, Int>()

        mTxns.filter { it.isExpense }.forEach { t ->
            val tSplits = splitsMap[t.id].orEmpty()
            if (tSplits.isNotEmpty()) {
                tSplits.filter { it.category != "Shared/Other" }.forEach { s ->
                    val cat = if (s.category == "My Expense") t.category else s.category
                    catMap[cat] = (catMap[cat] ?: 0.0) + s.amount
                    cntMap[cat] = (cntMap[cat] ?: 0) + 1
                }
            } else {
                catMap[t.category] = (catMap[t.category] ?: 0.0) + t.amount
                cntMap[t.category] = (cntMap[t.category] ?: 0) + 1
            }
        }

        mTxns.filter { it.isRefund }.forEach { t ->
            if (catMap.containsKey(t.category)) {
                catMap[t.category] = maxOf(0.0, (catMap[t.category] ?: 0.0) - t.amount)
            }
        }

        catMap.entries.filter { it.value > 0 }.sortedByDescending { it.value }.map { (cat, spent) ->
            CatSlice(cat, spent, b.find { it.category == cat }?.monthlyLimit ?: 0.0, CatColor.tone(cat), CatColor.bg(cat), CatColor.icon(cat), cntMap[cat] ?: 0)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _budgetPrediction = MutableStateFlow<String?>(null)
    val budgetPrediction: StateFlow<String?> = _budgetPrediction.asStateFlow()

    private val _lastAiReport = MutableStateFlow<AiReport?>(null)
    val lastAiReport: StateFlow<AiReport?> = _lastAiReport.asStateFlow()

    private val _bills = MutableStateFlow<List<com.example.smsaggregator.data.CreditCardBill>>(emptyList())
    val bills: StateFlow<List<com.example.smsaggregator.data.CreditCardBill>> = _bills.asStateFlow()

    // When set, the UI navigates to the Budgets tab and opens this category's transaction list.
    private val _pendingCategory = MutableStateFlow<String?>(null)
    val pendingCategory: StateFlow<String?> = _pendingCategory.asStateFlow()

    fun openCategory(category: String) { _pendingCategory.value = category }
    fun clearPendingCategory() { _pendingCategory.value = null }

    init {
        // Self-healing: clear false positives, fix AI misclassifications, and refresh transaction metadata
        viewModelScope.launch(Dispatchers.IO) {
            // Seed default categories if none exist
            val defaults = listOf(
                com.example.smsaggregator.data.UserCategory("Food & Dining", "#FF7043", "restaurant", false),
                com.example.smsaggregator.data.UserCategory("Groceries", "#66BB6A", "shopping_cart", false),
                com.example.smsaggregator.data.UserCategory("Fuel", "#FFA726", "local_gas_station", false),
                com.example.smsaggregator.data.UserCategory("Shopping", "#AB47BC", "shopping_bag", false),
                com.example.smsaggregator.data.UserCategory("Travel", "#42A5F5", "directions_car", false),
                com.example.smsaggregator.data.UserCategory("Utilities", "#26C6DA", "bolt", false),
                com.example.smsaggregator.data.UserCategory("Entertainment", "#EC407A", "movie", false),
                com.example.smsaggregator.data.UserCategory("Investments", "#5C6BC0", "trending_up", false),
                com.example.smsaggregator.data.UserCategory("Health", "#EF5350", "medical_services", false),
                com.example.smsaggregator.data.UserCategory("Other", "#78909C", "category", false)
            )
            
            categoryDao.getAllCategories().collect { existing ->
                if (existing.isEmpty()) {
                    defaults.forEach { categoryDao.insertCategory(it) }
                }
                _categories.value = existing
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            subscriptionDao.getActiveSubscriptions().collect {
                _subscriptions.value = it
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            transactionDao.getAllTransactions().collect {
                _transactions.value = it
                detectSubscriptions(it)
                updateWidgetSnapshot()
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            budgetDao.getAllBudgets().collect {
                _budgets.value = it
                updateWidgetSnapshot()
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            splitDao.getAllSplits().collect {
                _allSplits.value = it
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            creditCardBillDao.getAllBills().collect {
                _bills.value = it
            }
        }

        // One-time self-heal (v9): re-tag already-stored credit-card bill payments and
        // self-transfers so they stop double-counting in spend. Only flips the new
        // isTransfer flag — never touches user-corrected categories. Runs once.
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = getApplication<Application>()
                .getSharedPreferences("sms_agg_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("transfer_retag_v9_done", false)) {
                try {
                    transactionDao.getAllTransactionsList().forEach { txn ->
                        if (txn.rawSms.isNotBlank()) {
                            val shouldBeTransfer =
                                com.example.smsaggregator.logic.SmsParser.isTransferSms(txn.rawSms)
                            if (shouldBeTransfer && !txn.isTransfer) {
                                transactionDao.updateTransfer(txn.id, true)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TransactionVM", "Transfer re-tag failed", e)
                }
                prefs.edit().putBoolean("transfer_retag_v9_done", true).apply()
            }
        }

        // Restore user profile if already signed in
        if (authRepository.isSignedIn) {
            updateUserProfile()
        }
    }

    // ───────── AUTH ─────────

    fun getSignInIntent(): Intent? {
        return authRepository.getSignInIntent()
    }

    fun handleSignInResult(result: ActivityResult) {
        viewModelScope.launch(Dispatchers.IO) {
            _authState.value = AuthState.LOADING
            try {
                val data = result.data
                if (data == null) {
                    _authState.value = AuthState.SIGNED_OUT
                    _syncStatus.value = "❌ Sign-in cancelled"
                    return@launch
                }
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken

                if (idToken != null) {
                    val user = authRepository.firebaseAuthWithGoogle(idToken)
                    if (user != null) {
                        _authState.value = AuthState.SIGNED_IN
                        updateUserProfile()
                        // Trigger full sync on login
                        syncWithCloud()
                    } else {
                        _authState.value = AuthState.SIGNED_OUT
                        _syncStatus.value = "❌ Sign-in failed"
                    }
                } else {
                    _authState.value = AuthState.SIGNED_OUT
                    _syncStatus.value = "❌ No ID token received"
                }
            } catch (e: Exception) {
                android.util.Log.e("TransactionVM", "Sign-in error", e)
                _authState.value = AuthState.SIGNED_OUT
                _syncStatus.value = "❌ Sign-in error: ${e.message?.take(50)}"
            }
        }
    }

    fun signOut() {
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.signOut()
            _authState.value = AuthState.SIGNED_OUT
            _userProfile.value = UserProfile()
            _syncStatus.value = null
        }
    }

    private fun updateUserProfile() {
        _userProfile.value = UserProfile(
            displayName = authRepository.displayName,
            email = authRepository.email,
            photoUrl = authRepository.photoUrl
        )
    }

    // ───────── CLOUD SYNC ─────────

    fun syncWithCloud() {
        val uid = authRepository.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _syncStatus.value = "☁️ Syncing..."
            try {
                val result = firestoreSync.fullSync(uid)
                _syncStatus.value = "✅ Synced! (${result.downloadedTransactions} restored)"
            } catch (e: Exception) {
                _syncStatus.value = "❌ Sync failed"
            }
        }
    }

    private fun pushToCloud() {
        val uid = authRepository.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            firestoreSync.uploadAllTransactions(uid)
            firestoreSync.uploadAllOverrides(uid)
        }
    }

    fun setTransactionIgnored(transaction: Transaction, ignored: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            transactionDao.updateIgnored(transaction.id, ignored, now)
            val updated = transaction.copy(isIgnored = ignored, updatedAt = now)
            authRepository.uid?.let { uid ->
                firestoreSync.uploadTransaction(uid, updated)
            }
        }
    }

    fun updateTransactionReceipt(transaction: Transaction, path: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = transaction.copy(receiptPath = path, updatedAt = System.currentTimeMillis())
            transactionDao.updateTransactions(listOf(updated))
            if (authRepository.isSignedIn) {
                authRepository.uid?.let { uid ->
                    firestoreSync.uploadTransaction(uid, updated)
                }
            }
        }
    }

    // ───────── TRANSACTIONS ─────────

    fun insertTransactions(newTransactions: List<Transaction>) {
        viewModelScope.launch(Dispatchers.IO) {
            transactionDao.insertAll(newTransactions)
            // Auto-sync to cloud if signed in
            if (authRepository.isSignedIn) {
                pushToCloud()
            }
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) {
            transactionDao.deleteTransaction(transaction)
            if (authRepository.isSignedIn) {
                authRepository.uid?.let { uid ->
                    firestoreSync.deleteTransactions(uid, listOf(transaction))
                }
            }
        }
    }

    // ───────── BULK ACTIONS ─────────

    fun bulkSetIgnored(transactions: List<Transaction>, ignored: Boolean) {
        if (transactions.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            transactionDao.updateIgnoredForIds(transactions.map { it.id }, ignored, now)
            authRepository.uid?.let { uid ->
                transactions.forEach { firestoreSync.uploadTransaction(uid, it.copy(isIgnored = ignored, updatedAt = now)) }
            }
        }
    }

    fun bulkSetCategory(transactions: List<Transaction>, category: String) {
        if (transactions.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            transactionDao.updateCategoryForIds(transactions.map { it.id }, category)
            if (authRepository.isSignedIn) pushToCloud()
        }
    }

    fun bulkDelete(transactions: List<Transaction>) {
        if (transactions.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            transactionDao.deleteTransactions(transactions)
            authRepository.uid?.let { uid ->
                firestoreSync.deleteTransactions(uid, transactions)
            }
        }
    }

    // ───────── WIDGET & ALERTS ─────────

    private fun monthStart(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** Writes a small snapshot for the home-screen widget and asks it to refresh. */
    private fun updateWidgetSnapshot() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val start = monthStart()
                val txns = _transactions.value
                val spent = txns.filter { it.isExpense && it.date >= start }.sumOf { it.amount } -
                    txns.filter { it.isRefund && it.date >= start }.sumOf { it.amount }
                val income = txns.filter { it.isIncome && it.date >= start }.sumOf { it.amount }
                val budget = _budgets.value.sumOf { it.monthlyLimit + it.rolloverAmount }
                val app = getApplication<Application>()
                app.getSharedPreferences("sms_agg_prefs", Context.MODE_PRIVATE).edit()
                    .putFloat("widget_spent", maxOf(0.0, spent).toFloat())
                    .putFloat("widget_income", income.toFloat())
                    .putFloat("widget_budget", budget.toFloat())
                    .putLong("widget_updated", System.currentTimeMillis())
                    .apply()
                com.example.smsaggregator.widget.ExpenseWidgetProvider.requestUpdate(app)
            } catch (e: Exception) {
                Log.e("TransactionVM", "Widget snapshot failed", e)
            }
        }
    }

    /** Posts a notification when a category crosses 80% / 100% of its budget (once each per month). */
    private fun checkBudgetAlerts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val budgets = budgetDao.getAllBudgetsList()
                if (budgets.isEmpty()) return@launch
                val app = getApplication<Application>()
                val cal = Calendar.getInstance()
                val ym = "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH)}"
                val start = monthStart()
                cal.timeInMillis = start
                cal.add(Calendar.MONTH, 1)
                val end = cal.timeInMillis

                val totals = transactionDao.getCategorySpendBetween(start, end).associate { it.category to it.total }
                val prefs = app.getSharedPreferences("sms_agg_prefs", Context.MODE_PRIVATE)

                budgets.forEach { b ->
                    val limit = b.monthlyLimit + b.rolloverAmount
                    if (limit <= 0) return@forEach
                    val spent = totals[b.category] ?: 0.0
                    val pct = spent / limit
                    val k100 = "ba_${b.category}_${ym}_100"
                    val k80 = "ba_${b.category}_${ym}_80"
                    val spentStr = com.example.smsaggregator.logic.InsightsEngine.formatAmount(spent)
                    val limitStr = com.example.smsaggregator.logic.InsightsEngine.formatAmount(limit)
                    when {
                        pct >= 1.0 && !prefs.getBoolean(k100, false) -> {
                            com.example.smsaggregator.util.NotificationHelper.notifyBudget(
                                app, "100$ym${b.category}".hashCode(),
                                "${b.category} budget exceeded",
                                "You've spent $spentStr of your $limitStr ${b.category} budget."
                            )
                            prefs.edit().putBoolean(k100, true).apply()
                        }
                        pct >= 0.8 && !prefs.getBoolean(k80, false) -> {
                            com.example.smsaggregator.util.NotificationHelper.notifyBudget(
                                app, "80$ym${b.category}".hashCode(),
                                "${b.category} budget at ${(pct * 100).toInt()}%",
                                "You've spent $spentStr of your $limitStr ${b.category} budget."
                            )
                            prefs.edit().putBoolean(k80, true).apply()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TransactionVM", "Budget alert check failed", e)
            }
        }
    }

    // ───────── SEARCH & FILTERS ─────────
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setQuickFilter(filter: String?) {
        _quickFilter.value = if (_quickFilter.value == filter) null else filter // Toggle behavior
    }

    fun setSourceFilter(source: String?) {
        _sourceFilter.value = if (_sourceFilter.value == source) null else source
    }

    fun setDateRangeFilter(start: Long, end: Long) {
        _dateRangeFilter.value = start to end
    }

    fun clearDateRangeFilter() {
        _dateRangeFilter.value = null
    }

    fun getUniqueSources(): List<String> {
        return _transactions.value.map { it.source }.distinct().sorted()
    }

    fun getFilteredTransactions(): List<Transaction> {
        var filteredList = _transactions.value
        val query = _searchQuery.value.trim().lowercase()

        // 1. Apply Text Search
        if (query.isNotEmpty()) {
            filteredList = filteredList.filter {
                it.merchant.lowercase().contains(query) ||
                it.category.lowercase().contains(query) ||
                it.amount.toString().contains(query)
            }
        }

        // 2. Apply Quick Filter
        filteredList = when (_quickFilter.value) {
            "> ₹5,000" -> filteredList.filter { it.amount > 5000 && it.type.name == "DEBIT" }
            "Debits Only" -> filteredList.filter { it.type.name == "DEBIT" }
            "Credits Only" -> filteredList.filter { it.type.name == "CREDIT" }
            else -> filteredList
        }

        // 3. Apply Source Filter
        _sourceFilter.value?.let { src ->
            filteredList = filteredList.filter { it.source == src }
        }

        // 4. Apply Date Range Filter
        _dateRangeFilter.value?.let { (start, end) ->
            filteredList = filteredList.filter { it.date in start..end }
        }

        return filteredList
    }

    fun applyBudgetRollover() {
        viewModelScope.launch(Dispatchers.IO) {
            val allBudgets: List<Budget> = budgetDao.getAllBudgetsList()
            val allTxns: List<Transaction> = transactionDao.getAllTransactionsList()
            
            val curCal = Calendar.getInstance()
            val curYear = curCal.get(Calendar.YEAR)
            val curMonth = curCal.get(Calendar.MONTH)

            val lastMonthCal = Calendar.getInstance()
            lastMonthCal.add(Calendar.MONTH, -1)
            val prevYear = lastMonthCal.get(Calendar.YEAR)
            val prevMonth = lastMonthCal.get(Calendar.MONTH)
            
            for (budget in allBudgets) {
                // Prevent applying rollover for the same current month multiple times
                if (budget.lastRolloverMonth == curMonth && budget.lastRolloverYear == curYear) continue

                val spentLastMonth = allTxns.filter {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = it.date
                    it.category == budget.category && 
                    cal.get(Calendar.YEAR) == prevYear && 
                    cal.get(Calendar.MONTH) == prevMonth &&
                    it.isExpense
                }.sumOf { it.amount } -
                    // Net refunds in that category/month so rollover reflects true spend.
                    allTxns.filter {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = it.date
                        it.category == budget.category &&
                        cal.get(Calendar.YEAR) == prevYear &&
                        cal.get(Calendar.MONTH) == prevMonth &&
                        it.isRefund
                    }.sumOf { it.amount }
                val spentLastMonthClamped = maxOf(0.0, spentLastMonth)
                
                // Cumulative logic: budget + existing rollover - spent
                val prevTotalBudget = budget.monthlyLimit + budget.rolloverAmount
                val remaining = maxOf(0.0, prevTotalBudget - spentLastMonthClamped)
                
                if (remaining > 0 || budget.rolloverAmount > 0) {
                    budgetDao.insertBudget(budget.copy(
                        rolloverAmount = remaining,
                        lastRolloverMonth = curMonth,
                        lastRolloverYear = curYear
                    ))
                }
            }
        }
    }

    // ───────── BUDGETS & AI PREDICTION ─────────

    fun setBudget(category: String, limit: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            if (limit <= 0) {
                budgetDao.deleteBudget(category)
            } else {
                budgetDao.insertBudget(Budget(category, limit))
            }
        }
    }

    fun predictBudgetWithGemini(category: String, limit: Double, currentlySpent: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            _budgetPrediction.value = "⏳ Analyzing pacing..."
            
            val apiKey = com.example.smsaggregator.data.SecurePrefs.getGeminiApiKey(getApplication())

            if (apiKey.isBlank()) {
                _budgetPrediction.value = "⚠️ API Key required for predictions."
                return@launch
            }

            // Calculate days left in month for context
            val calendar = java.util.Calendar.getInstance()
            val totalDays = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            val currentDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
            val daysLeft = totalDays - currentDay

            val prediction = GeminiService.predictBudgetPacing(apiKey, category, limit, currentlySpent, currentDay, daysLeft)
            _budgetPrediction.value = prediction ?: "⚠️ Failed to reach Gemini."
        }
    }

    /** User manually corrects a category — saves to overrides AND updates all transactions globally */
    fun overrideCategory(transaction: Transaction, newCategory: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val key = transaction.merchant.lowercase().trim()
            
            // 1. Save merchant override for future learning
            val override = MerchantOverride(key, newCategory, "user")
            overrideDao.insertOverride(override)
            
            // 2. Update in-memory cache for immediate effect
            com.example.smsaggregator.logic.Categorizer.addOverride(key, newCategory)
            
            // 3. Global Propagation
            if (key == "unknown") {
                // If it's 'Unknown', propagate the category fix to all transactions with the same SMS
                transactionDao.updateMerchantAndCategoryBySms("Unknown", "Unknown", newCategory, transaction.rawSms)
            } else {
                // If it's a real name, update ALL entries for this merchant regardless of case
                transactionDao.updateCategoryByMerchantCaseInsensitive(key, newCategory)
            }

            // 4. Update the classification report in-memory if it exists
            _lastAiReport.value?.let { report ->
                val updatedItems = report.items.map { item ->
                    if (item.sms == transaction.rawSms) item.copy(category = newCategory, isSorted = true)
                    else item
                }
                _lastAiReport.value = AiReport(updatedItems)
            }

            // 5. Sync to cloud
            if (authRepository.isSignedIn) {
                authRepository.uid?.let { uid ->
                    firestoreSync.uploadOverride(uid, override)
                    pushToCloud()
                }
            }
        }
    }

    /** Mapping from AI Review Tab: Renames merchant and sets category for all matching transactions */
    fun mapAiReportItem(item: AiReportItem, customMerchant: String, selectedCategory: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newMerchantName = customMerchant.trim().ifBlank { item.merchant }
            val key = newMerchantName.lowercase().trim()

            // 1. Save override
            val override = MerchantOverride(key, selectedCategory, "user")
            overrideDao.insertOverride(override)
            com.example.smsaggregator.logic.Categorizer.addOverride(key, selectedCategory)

            // 2. Update DB: Set merchant name AND category for all transactions with this SMS
            transactionDao.updateMerchantAndCategoryBySms(item.merchant, newMerchantName, selectedCategory, item.sms)

            // 3. Update AI report state for immediate UI UI feedback
            _lastAiReport.value?.let { report ->
                val updatedItems = report.items.map { itItem ->
                    if (itItem.sms == item.sms) itItem.copy(merchant = newMerchantName, category = selectedCategory, isSorted = true)
                    else itItem
                }
                _lastAiReport.value = AiReport(updatedItems)
            }

            // 4. Sync
            if (authRepository.isSignedIn) {
                authRepository.uid?.let { uid ->
                    firestoreSync.uploadOverride(uid, override)
                    pushToCloud()
                }
            }
        }
    }

    /** Call Gemini AI to classify all "Other" category merchants */
    fun classifyWithGemini() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isClassifying.value = true
                _classifyResult.value = null

                // Get API key from secure storage
                val apiKey = com.example.smsaggregator.data.SecurePrefs.getGeminiApiKey(getApplication())

                if (apiKey.isBlank()) {
                    _classifyResult.value = "NO_KEY"
                    _isClassifying.value = false
                    return@launch
                }

                // Find transactions to classify: "Other" category or "Unknown" merchants
                val targets = _transactions.value
                    .filter { it.category == "Other" || it.merchant.lowercase() == "unknown" }
                    .distinctBy { it.merchant + it.rawSms } // Deduplicate identical context
                    .take(8) // Keep batches small to avoid free-tier rate limits (RPM)

                if (targets.isEmpty()) {
                    _classifyResult.value = "NONE"
                    _isClassifying.value = false
                    return@launch
                }

                val contexts = targets.map { 
                    GeminiService.MerchantContext(it.merchant, it.rawSms)
                }

                val result = GeminiService.classifyMerchants(apiKey, contexts)

                result.onSuccess { results ->
                    if (results.isNotEmpty()) {
                        // Create report items
                        val reportItems = results.map { res ->
                            AiReportItem(res.originalName, res.category, res.rawSms, res.category != "Other")
                        }
                        _lastAiReport.value = AiReport(reportItems)

                        // Save all as overrides
                        val overrides = results.map { res ->
                            MerchantOverride(res.originalName.lowercase().trim(), res.category, "gemini")
                        }
                        overrideDao.insertAll(overrides)

                        // Update existing transactions
                        for (res in results) {
                            if (res.originalName.lowercase() == "unknown") {
                                // For unknown, update any transaction matching this exact SMS context
                                transactionDao.updateMerchantAndCategoryBySms("Unknown", "Unknown", res.category, res.rawSms)
                            } else {
                                // For named merchants, update all entries case-insensitively
                                transactionDao.updateCategoryByMerchantCaseInsensitive(res.originalName.lowercase().trim(), res.category)
                            }
                        }

                        // Calculate stats for feedback
                        val recognizedCount = results.count { it.category != "Other" }
                        val unrecognizedCount = targets.size - recognizedCount
                        
                        _classifyResult.value = "OK:$recognizedCount:$unrecognizedCount"
                    } else {
                        _classifyResult.value = "NONE"
                    }

                    // Sync to cloud
                    if (authRepository.isSignedIn) pushToCloud()
                }.onFailure { exception ->
                    val errorMsg = exception.message ?: "FAIL"
                    _classifyResult.value = if (errorMsg.startsWith("AI_ERROR") || errorMsg.startsWith("API_ERROR_")) errorMsg else "API_ERROR_Unknown:$errorMsg"
                    Log.e("TransactionViewModel", "AI Classification Exception: $errorMsg")
                }
            } catch (e: Exception) {
                Log.e("TransactionViewModel", "AI Classification crashed", e)
                _classifyResult.value = "API_ERROR_Crash:${e.message}"
            } finally {
                _isClassifying.value = false
            }
        }
    }

    fun saveApiKey(key: String) {
        com.example.smsaggregator.data.SecurePrefs.setGeminiApiKey(getApplication(), key)
        GeminiService.resetCache()
    }

    fun getApiKey(): String {
        return com.example.smsaggregator.data.SecurePrefs.getGeminiApiKey(getApplication())
    }

    fun setDarkMode(isDark: Boolean) {
        val prefs = getApplication<Application>().getSharedPreferences("sms_agg_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("dark_mode", isDark).apply()
        _isDarkMode.value = isDark
    }

    fun setBiometricEnabled(isEnabled: Boolean) {
        val prefs = getApplication<Application>().getSharedPreferences("sms_agg_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("biometric_lock", isEnabled).apply()
        _isBiometricEnabled.value = isEnabled
    }

    fun clearClassifyResult() {
        _classifyResult.value = null
    }

    fun clearSyncStatus() {
        _syncStatus.value = null
    }

    // ───────── SMS SCANNING ─────────

    fun refreshSms(forceFull: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            val context = getApplication<Application>()
            val prefs = context.getSharedPreferences("sms_agg_prefs", Context.MODE_PRIVATE)

            // 1. Load latest overrides
            val currentOverrides = overrideDao.getAllOverrides().associate { it.merchantKey to it.category }
            com.example.smsaggregator.logic.Categorizer.loadOverrides(currentOverrides)

            // 2. Incremental scan: only read messages newer than the last successful scan.
            //    forceFull (e.g. after a parser upgrade) re-reads the whole inbox.
            val lastScan = if (forceFull) 0L else prefs.getLong("last_sms_scan_date", 0L)
            val selection = if (lastScan > 0L) "date > ?" else null
            val selectionArgs = if (lastScan > 0L) arrayOf(lastScan.toString()) else null

            val cursor: android.database.Cursor? = context.contentResolver.query(
                android.net.Uri.parse("content://sms/inbox"),
                arrayOf("body", "address", "date"),
                selection,
                selectionArgs,
                "date DESC"
            )

            val newTransactions = mutableListOf<Transaction>()
            val newBills = mutableListOf<com.example.smsaggregator.data.CreditCardBill>()
            var maxDate = lastScan

            cursor?.use {
                val bodyIndex = it.getColumnIndex("body")
                val addressIndex = it.getColumnIndex("address")
                val dateIndex = it.getColumnIndex("date")
                
                if (bodyIndex == -1 || dateIndex == -1 || addressIndex == -1) {
                    _isScanning.value = false
                    return@use
                }

                while (it.moveToNext()) {
                    val body = it.getString(bodyIndex)
                    val address = it.getString(addressIndex) ?: ""
                    val dateMills = it.getLong(dateIndex)
                    if (dateMills > maxDate) maxDate = dateMills
                    
                    val transaction = com.example.smsaggregator.logic.SmsParser.parseSms(body, address, dateMills)
                    if (transaction != null) {
                        newTransactions.add(transaction)
                    }

                    val bill = com.example.smsaggregator.logic.SmsParser.parseBillSms(body, address, dateMills)
                    if (bill != null) {
                        newBills.add(bill)
                    }

                    // Auto-reconciliation: Check if this is a payment confirmation
                    reconcilePayment(body)
                }
            }

            // 3. Insert into DB (Room handles duplicates via IGNORE strategy)
            if (newTransactions.isNotEmpty()) {
                transactionDao.insertAll(newTransactions)
                if (authRepository.isSignedIn) pushToCloud()
            }

            if (newBills.isNotEmpty()) {
                newBills.forEach { bill ->
                    val existing = creditCardBillDao.findDuplicateBill(bill.bankName, bill.cardDigits, bill.totalDue, bill.dueDate)
                    if (existing == null) {
                        creditCardBillDao.insertBill(bill)
                    }
                }
            }

            // 4. Remember how far we scanned so the next run is incremental.
            if (maxDate > lastScan) {
                prefs.edit().putLong("last_sms_scan_date", maxDate).apply()
            }
            checkBudgetAlerts()
            _isScanning.value = false
        }
    }

    private suspend fun reconcilePayment(smsBody: String) {
        val lower = smsBody.lowercase()
        if (lower.contains("payment") && (lower.contains("received") || lower.contains("successful") || lower.contains("thank you"))) {
            val bankMatch = listOf("hdfc", "sbi", "icici", "axis", "kotak", "pnb", "citi", "amex", "idfc", "rbl").find { lower.contains(it) }
            val digitMatch = Regex("(?i)(?:card|acc|acct|ending in)\\s*(?:no\\.?)?\\s*[Xx*]*(\\d{3,4})").find(smsBody)
                ?: Regex("(?i)[Xx*]+(\\d{3,4})").find(smsBody)
            
            val digits = digitMatch?.groupValues?.get(1)
            
            if (bankMatch != null && digits != null) {
                val bankName = when (bankMatch) {
                    "hdfc" -> "HDFC Bank"
                    "sbi" -> "SBI Card"
                    "icici" -> "ICICI Bank"
                    "axis" -> "Axis Bank"
                    "kotak" -> "Kotak Bank"
                    "pnb" -> "PNB"
                    "citi" -> "Citi Bank"
                    "amex" -> "Amex"
                    "idfc" -> "IDFC First"
                    "rbl" -> "RBL Bank"
                    else -> bankMatch
                }
                creditCardBillDao.markAsPaid(bankName, "XX$digits")
            }
        }
    }

    fun markBillAsPaid(billId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            creditCardBillDao.markAsPaidById(billId)
        }
    }

    // ───────── TRANSACTIONS ─────────

    fun insertManualTransaction(amount: Double, merchant: String, category: String, date: Long, type: com.example.smsaggregator.data.TransactionType, source: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val txn = Transaction(
                amount = amount,
                merchant = merchant,
                category = category,
                date = date,
                type = type,
                source = source,
                isManual = true,
                updatedAt = System.currentTimeMillis()
            )
            transactionDao.insertAll(listOf(txn))
            if (authRepository.isSignedIn) pushToCloud()
            checkBudgetAlerts()
        }
    }

    fun splitTransaction(transactionId: Long, splits: List<com.example.smsaggregator.data.TransactionSplit>) {
        viewModelScope.launch(Dispatchers.IO) {
            splitDao.deleteSplitsForTransaction(transactionId)
            splitDao.insertSplits(splits)
        }
    }

    fun getSplitsForTransaction(transactionId: Long) = splitDao.getSplitsForTransaction(transactionId)

    // ───────── CATEGORIES ─────────

    fun addCategory(name: String, colorHex: String, iconName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            categoryDao.insertCategory(com.example.smsaggregator.data.UserCategory(name, colorHex, iconName))
        }
    }

    fun deleteCategory(category: com.example.smsaggregator.data.UserCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            categoryDao.deleteCategory(category)
        }
    }

    // ───────── EXPORT ─────────

    fun exportTransactionsToCsv(txns: List<Transaction>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (txns.isEmpty()) return@launch
            val csv = StringBuilder("Date,Merchant,Amount,Category,Source,Type,Manual,Ignored,SMS\n")
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            
            txns.forEach { t ->
                csv.append("${fmt.format(Date(t.date))},")
                csv.append("\"${t.merchant.replace("\"", "\"\"")}\",")
                csv.append("${t.amount},")
                csv.append("\"${t.category.replace("\"", "\"\"")}\",")
                csv.append("\"${t.source.replace("\"", "\"\"")}\",")
                csv.append("${t.type.name},")
                csv.append("${t.isManual},")
                csv.append("${t.isIgnored},")
                csv.append("\"${t.rawSms.replace("\"", "\"\"").replace("\n", " ")}\"\n")
            }

            val context = getApplication<Application>()
            val filename = "transactions_${System.currentTimeMillis()}.csv"
            val file = java.io.File(context.cacheDir, filename)
            file.writeText(csv.toString())

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Export Transactions").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    // ───────── MONTHLY PDF REPORT ─────────

    fun exportMonthlyReportPdf() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val start = monthStart()
                val monthTxns = _transactions.value.filter { it.date >= start }
                val spent = monthTxns.filter { it.isExpense }.sumOf { it.amount } -
                    monthTxns.filter { it.isRefund }.sumOf { it.amount }
                val income = monthTxns.filter { it.isIncome }.sumOf { it.amount }
                val net = income - spent

                val catMap = HashMap<String, Double>()
                monthTxns.filter { it.isExpense }.forEach { catMap[it.category] = (catMap[it.category] ?: 0.0) + it.amount }
                monthTxns.filter { it.isRefund }.forEach {
                    if (catMap.containsKey(it.category)) catMap[it.category] = maxOf(0.0, (catMap[it.category] ?: 0.0) - it.amount)
                }
                val topCats = catMap.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(8)
                val maxCat = topCats.maxOfOrNull { it.value } ?: 1.0

                fun money(v: Double) = com.example.smsaggregator.logic.InsightsEngine.formatAmount(v)

                val doc = android.graphics.pdf.PdfDocument()
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas
                val paint = android.graphics.Paint().apply { isAntiAlias = true }
                val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())

                var y = 64f
                paint.color = android.graphics.Color.parseColor("#111418")
                paint.textSize = 28f; paint.isFakeBoldText = true
                canvas.drawText("Expense Report", 40f, y, paint)
                paint.textSize = 14f; paint.isFakeBoldText = false; paint.color = android.graphics.Color.GRAY
                y += 24f; canvas.drawText(monthName, 40f, y, paint)

                // Summary rows
                y += 48f
                fun summary(label: String, value: String, colorHex: String) {
                    paint.color = android.graphics.Color.GRAY; paint.textSize = 13f
                    canvas.drawText(label, 40f, y, paint)
                    paint.color = android.graphics.Color.parseColor(colorHex); paint.textSize = 18f; paint.isFakeBoldText = true
                    canvas.drawText(value, 300f, y, paint)
                    paint.isFakeBoldText = false
                    y += 32f
                }
                summary("Total spent", money(spent), "#EE5253")
                summary("Income received", money(income), "#1DD1A1")
                summary(if (net >= 0) "Net saved" else "Overspent", money(kotlin.math.abs(net)), if (net >= 0) "#1DD1A1" else "#EE5253")

                // Category breakdown
                y += 20f
                paint.color = android.graphics.Color.parseColor("#111418"); paint.textSize = 18f; paint.isFakeBoldText = true
                canvas.drawText("Top categories", 40f, y, paint); paint.isFakeBoldText = false
                y += 28f
                topCats.forEach { (cat, amt) ->
                    paint.color = android.graphics.Color.parseColor("#333333"); paint.textSize = 13f
                    canvas.drawText(cat, 40f, y, paint)
                    canvas.drawText(money(amt), 470f, y, paint)
                    // bar
                    val barW = (amt / maxCat * 400.0).toFloat()
                    paint.color = android.graphics.Color.parseColor("#2E86DE")
                    canvas.drawRect(40f, y + 6f, 40f + barW, y + 12f, paint)
                    y += 36f
                }

                paint.color = android.graphics.Color.LTGRAY; paint.textSize = 10f
                canvas.drawText("Generated by Expense Tracker • ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}", 40f, 820f, paint)

                doc.finishPage(page)
                val file = java.io.File(app.cacheDir, "expense_report_${System.currentTimeMillis()}.pdf")
                java.io.FileOutputStream(file).use { doc.writeTo(it) }
                doc.close()

                val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(Intent.createChooser(intent, "Share Report").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            } catch (e: Exception) {
                Log.e("TransactionVM", "PDF report failed", e)
                _syncStatus.value = "❌ Report generation failed"
            }
        }
    }

    // ───────── LOCAL BACKUP / RESTORE (JSON) ─────────

    /** Writes a full JSON backup (transactions + overrides + budgets) to the chosen file. */
    fun exportBackupToUri(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val txns = transactionDao.getAllTransactionsList()
                val overrides = overrideDao.getAllOverrides()
                val budgets = budgetDao.getAllBudgetsList()

                val root = org.json.JSONObject()
                root.put("version", 1)
                root.put("exportedAt", System.currentTimeMillis())

                val tArr = org.json.JSONArray()
                txns.forEach { t ->
                    tArr.put(org.json.JSONObject().apply {
                        put("amount", t.amount)
                        put("merchant", t.merchant)
                        put("category", t.category)
                        put("date", t.date)
                        put("type", t.type.name)
                        put("source", t.source)
                        put("rawSms", t.rawSms)
                        put("isIgnored", t.isIgnored)
                        put("isManual", t.isManual)
                        put("isTransfer", t.isTransfer)
                        put("updatedAt", t.updatedAt)
                    })
                }
                root.put("transactions", tArr)

                val oArr = org.json.JSONArray()
                overrides.forEach { o ->
                    oArr.put(org.json.JSONObject().apply {
                        put("merchantKey", o.merchantKey)
                        put("category", o.category)
                        put("source", o.source)
                    })
                }
                root.put("overrides", oArr)

                val bArr = org.json.JSONArray()
                budgets.forEach { b ->
                    bArr.put(org.json.JSONObject().apply {
                        put("category", b.category)
                        put("monthlyLimit", b.monthlyLimit)
                        put("rolloverAmount", b.rolloverAmount)
                        put("lastRolloverMonth", b.lastRolloverMonth)
                        put("lastRolloverYear", b.lastRolloverYear)
                    })
                }
                root.put("budgets", bArr)

                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(root.toString(2).toByteArray(Charsets.UTF_8))
                }
                _syncStatus.value = "✅ Backup saved (${txns.size} transactions)"
            } catch (e: Exception) {
                Log.e("TransactionVM", "Backup failed", e)
                _syncStatus.value = "❌ Backup failed"
            }
        }
    }

    /** Restores a JSON backup, merging into the local DB (duplicates ignored). */
    fun importBackupFromUri(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = getApplication<Application>().contentResolver
                    .openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (text.isNullOrBlank()) {
                    _syncStatus.value = "❌ Empty backup file"
                    return@launch
                }
                val root = org.json.JSONObject(text)

                val tArr = root.optJSONArray("transactions") ?: org.json.JSONArray()
                val txns = mutableListOf<Transaction>()
                for (i in 0 until tArr.length()) {
                    val o = tArr.getJSONObject(i)
                    txns.add(Transaction(
                        amount = o.getDouble("amount"),
                        merchant = o.getString("merchant"),
                        category = o.optString("category", "Other"),
                        date = o.getLong("date"),
                        type = com.example.smsaggregator.data.TransactionType.valueOf(o.optString("type", "DEBIT")),
                        source = o.optString("source", "Unknown"),
                        rawSms = o.optString("rawSms", ""),
                        isIgnored = o.optBoolean("isIgnored", false),
                        isManual = o.optBoolean("isManual", false),
                        isTransfer = o.optBoolean("isTransfer", false),
                        updatedAt = o.optLong("updatedAt", 0L)
                    ))
                }
                if (txns.isNotEmpty()) transactionDao.insertAll(txns)

                root.optJSONArray("overrides")?.let { arr ->
                    val list = mutableListOf<MerchantOverride>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(MerchantOverride(o.getString("merchantKey"), o.getString("category"), o.optString("source", "import")))
                    }
                    if (list.isNotEmpty()) overrideDao.insertAll(list)
                }

                root.optJSONArray("budgets")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        budgetDao.insertBudget(Budget(
                            o.getString("category"),
                            o.getDouble("monthlyLimit"),
                            o.optDouble("rolloverAmount", 0.0),
                            o.optInt("lastRolloverMonth", -1),
                            o.optInt("lastRolloverYear", -1)
                        ))
                    }
                }

                // Refresh the in-memory override cache and push the merged set to cloud.
                val current = overrideDao.getAllOverrides().associate { it.merchantKey to it.category }
                com.example.smsaggregator.logic.Categorizer.loadOverrides(current)
                if (authRepository.isSignedIn) pushToCloud()

                _syncStatus.value = "✅ Restored ${txns.size} transactions"
            } catch (e: Exception) {
                Log.e("TransactionVM", "Restore failed", e)
                _syncStatus.value = "❌ Restore failed: invalid file"
            }
        }
    }

    fun deleteSubscription(subscription: com.example.smsaggregator.data.Subscription) {
        viewModelScope.launch(Dispatchers.IO) {
            subscriptionDao.deleteSubscription(subscription.id)
        }
    }

    // ───────── SUBSCRIPTIONS ─────────

    private fun detectSubscriptions(txns: List<Transaction>) {
        val subscriptionCategories = listOf(
            "Entertainment", "Telecom", "Utilities", "Rent", "EMI / Loan", "Insurance", "Investments", "Auto Debit"
        )
        val blacklistedCategories = listOf("Food & Dining", "Groceries", "Shopping", "Fuel", "Travel", "ATM")

        viewModelScope.launch(Dispatchers.IO) {
            val currentSubs = subscriptionDao.getActiveSubscriptionsList()
            val today = System.currentTimeMillis()
            
            // 1. RECONCILE: Remove invalid existing subscriptions
            currentSubs.forEach { sub ->
                val isBlacklisted = sub.category in blacklistedCategories
                val isUnknown = sub.merchant.lowercase() == "unknown"
                val merchantTxns = txns.filter { it.merchant.lowercase() == sub.merchant.lowercase() }
                val currentCategory = merchantTxns.firstOrNull()?.category ?: sub.category
                
                if (isBlacklisted || isUnknown || (currentCategory !in subscriptionCategories && !isKnownSubMerchant(sub.merchant))) {
                    subscriptionDao.deleteSubscription(sub.id)
                }
            }

            // 2. DETECT: Find recurring payments or mandates
            val debitTxns = txns.filter { 
                it.type == com.example.smsaggregator.data.TransactionType.DEBIT && 
                it.merchant.lowercase() != "unknown"
            }

            // Group by merchant and amount
            val groups = debitTxns.groupBy { it.merchant.lowercase() to it.amount }
            
            groups.forEach { (key, list) ->
                val (merchant, amount) = key
                val sorted = list.sortedBy { it.date }
                val lastTxn = sorted.last()
                
                // Criteria:
                // A. It's a known mandate/subscription merchant (keyword based) OR
                // B. It's in a subscription-prone category AND repeats monthly
                
                val isKnownSub = isKnownSubMerchant(merchant)
                var shouldBeSub = false
                
                if (isKnownSub) {
                    shouldBeSub = true
                } else if (list.size >= 2 && lastTxn.category in subscriptionCategories) {
                    val intervals = mutableListOf<Long>()
                    for (i in 1 until sorted.size) {
                        intervals.add(sorted[i].date - sorted[i-1].date)
                    }
                    val avgInterval = intervals.average()
                    val monthMs = 30L * 24 * 60 * 60 * 1000
                    if (avgInterval in (monthMs * 0.8)..(monthMs * 1.2)) {
                        shouldBeSub = true
                    }
                }

                if (shouldBeSub) {
                    // Calculate next date: The first occurrence of this date (day of month) that is >= Today
                    val cal = java.util.Calendar.getInstance()
                    cal.timeInMillis = lastTxn.date
                    val dayOfMonth = cal.get(java.util.Calendar.DAY_OF_MONTH)
                    
                    val nextCal = java.util.Calendar.getInstance()
                    nextCal.set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                    nextCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    nextCal.set(java.util.Calendar.MINUTE, 0)
                    nextCal.set(java.util.Calendar.SECOND, 0)
                    
                    // If the expected day this month has already passed, move to next month
                    if (nextCal.timeInMillis < today - (24 * 60 * 60 * 1000)) { 
                        nextCal.add(java.util.Calendar.MONTH, 1)
                    }

                    // Prevent duplicates and only insert if not blacklisted
                    val alreadyExists = currentSubs.any { it.merchant.lowercase() == merchant && it.amount == amount }
                    if (!alreadyExists && lastTxn.category !in blacklistedCategories) {
                        subscriptionDao.insertSubscription(com.example.smsaggregator.data.Subscription(
                            merchant = lastTxn.merchant,
                            amount = lastTxn.amount,
                            category = lastTxn.category,
                            frequencyDays = 30,
                            lastDate = lastTxn.date,
                            nextExpectedDate = nextCal.timeInMillis
                        ))
                    }
                }
            }
        }
    }

    private fun isKnownSubMerchant(merchant: String): Boolean {
        val lower = merchant.lowercase()
        return listOf(
            "netflix", "spotify", "hotstar", "youtube", "prime", "disney", "apple", 
            "rent", "gym", "broadband", "recharge", "insurance", "policy", "lic",
            "loan", "emi", "sip", "mutual fund", "zerodha", "groww", "tata sky", "airtel", "jio",
            "nach", "mandate", "standing instruction", "auto debit"
        ).any { lower.contains(it) }
    }
}

class TransactionViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TransactionViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
