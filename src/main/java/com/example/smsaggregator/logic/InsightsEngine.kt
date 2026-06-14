package com.example.smsaggregator.logic

import com.example.smsaggregator.data.Transaction
import com.example.smsaggregator.data.isExpense
import com.example.smsaggregator.data.isIncome
import com.example.smsaggregator.data.isRefund
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

data class InsightCard(
    val title: String,
    val description: String,
    val transactions: List<Transaction>,
    val highlightCategory: String? = null,
    val isTitleGradient: Boolean = false,
    val isPositiveTrend: Boolean? = null, // True = savings (Green), False = spending up (Red), null = neutral
    val subText: String? = null
)

object InsightsEngine {
    
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    fun formatAmount(amount: Double): String {
        return currencyFormat.format(amount).replace(".00", "")
    }

    private fun getStartOfDay(daysAgo: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getStartOfMonth(monthsAgo: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -monthsAgo)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun generateInsights(transactions: List<Transaction>): List<InsightCard> {
        val insights = mutableListOf<InsightCard>()
        if (transactions.isEmpty()) {
            insights.add(InsightCard("No Data", "No recent transactions found.", emptyList()))
            return insights
        }

        // All spending trends are based on money-out rows only; refunds are netted out.
        val spendTxns = transactions.filter { it.isExpense }
        val refundTxns = transactions.filter { it.isRefund }

        val todayStart = getStartOfDay(0)
        val yesterdayStart = getStartOfDay(1)
        val oneWeekAgo = getStartOfDay(7)
        val twoWeeksAgo = getStartOfDay(14)
        val thisMonthStart = getStartOfMonth(0)
        val lastMonthStart = getStartOfMonth(1)
        val currentDayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

        // 1. Daily Comparison (Today vs Yesterday)
        val todaysTransactions = spendTxns.filter { it.date >= todayStart }
        val yesterdaysTransactions = spendTxns.filter { it.date in yesterdayStart until todayStart }
        val todaysTotal = maxOf(0.0, todaysTransactions.sumOf { it.amount } -
            refundTxns.filter { it.date >= todayStart }.sumOf { it.amount })
        val yesterdaysTotal = maxOf(0.0, yesterdaysTransactions.sumOf { it.amount } -
            refundTxns.filter { it.date in yesterdayStart until todayStart }.sumOf { it.amount })

        if (todaysTotal > 0 || yesterdaysTotal > 0) {
            val diff = todaysTotal - yesterdaysTotal
            val percentage = if (yesterdaysTotal > 0) (diff / yesterdaysTotal) * 100 else 100.0
            
            val (desc, isPositive, sub) = when {
                diff > 0 -> Triple(
                    "You've spent ${formatAmount(todaysTotal)} today.",
                    false,
                    "🔺 Up ${percentage.toInt()}% from yesterday"
                )
                diff < 0 -> Triple(
                    "You've spent ${formatAmount(todaysTotal)} today.",
                    true,
                    "🔻 Down ${Math.abs(percentage).toInt()}% from yesterday"
                )
                else -> Triple(
                    "You've spent ${formatAmount(todaysTotal)} today.",
                    null,
                    "◽ Same as yesterday"
                )
            }

            insights.add(InsightCard(
                title = "Today's Pulse",
                description = desc,
                transactions = todaysTransactions,
                isPositiveTrend = isPositive,
                subText = sub,
                isTitleGradient = true
            ))
        }

        // 2. Weekly Trend (This Week vs Last Week)
        val thisWeekTxns = spendTxns.filter { it.date in oneWeekAgo until todayStart + 86400000 }
        val lastWeekTxns = spendTxns.filter { it.date in twoWeeksAgo until oneWeekAgo }
        val thisWeekTotal = maxOf(0.0, thisWeekTxns.sumOf { it.amount } -
            refundTxns.filter { it.date in oneWeekAgo until todayStart + 86400000 }.sumOf { it.amount })
        val lastWeekTotal = maxOf(0.0, lastWeekTxns.sumOf { it.amount } -
            refundTxns.filter { it.date in twoWeeksAgo until oneWeekAgo }.sumOf { it.amount })

        if (thisWeekTotal > 0 || lastWeekTotal > 0) {
            val diff = thisWeekTotal - lastWeekTotal
            val percentage = if (lastWeekTotal > 0) (diff / lastWeekTotal) * 100 else 100.0
            
            val (desc, isPositive, sub) = when {
                diff > 0 -> Triple(
                    "You've spent ${formatAmount(thisWeekTotal)} this week.",
                    false,
                    "📈 Up ${percentage.toInt()}% (${formatAmount(diff)}) from last week"
                )
                diff < 0 -> Triple(
                    "You've spent ${formatAmount(thisWeekTotal)} this week.",
                    true,
                    "📉 Down ${Math.abs(percentage).toInt()}% (saved ${formatAmount(Math.abs(diff))}) from last week"
                )
                else -> Triple(
                    "You've spent ${formatAmount(thisWeekTotal)} this week.",
                    null,
                    "➖ Exact same as last week!"
                )
            }

            insights.add(InsightCard(
                title = "Weekly Pacing",
                description = desc,
                transactions = thisWeekTxns,
                isPositiveTrend = isPositive,
                subText = sub
            ))
        }

        // 3. Category Spike (Biggest Increase This Week)
        if (thisWeekTxns.isNotEmpty() && lastWeekTxns.isNotEmpty()) {
            val thisWeekCats = thisWeekTxns.groupBy { it.category }.mapValues { it.value.sumOf { t -> t.amount } }
            val lastWeekCats = lastWeekTxns.groupBy { it.category }.mapValues { it.value.sumOf { t -> t.amount } }

            var topSpikeCategory: String? = null
            var maxSpikeAmount = 0.0

            for ((category, currentAmount) in thisWeekCats) {
                if (category == "Other" || category == "Investments" || category == "UPI Transfer") continue
                val lastAmount = lastWeekCats[category] ?: 0.0
                val diff = currentAmount - lastAmount
                if (diff > maxSpikeAmount && diff > 500) { // Only highlight meaningful spikes > Rs.500
                    maxSpikeAmount = diff
                    topSpikeCategory = category
                }
            }

            if (topSpikeCategory != null) {
                val currentAmt = thisWeekCats[topSpikeCategory] ?: 0.0
                val pastAmt = lastWeekCats[topSpikeCategory] ?: 0.0
                
                insights.add(InsightCard(
                    title = "Category Alert",
                    description = "Your spending on $topSpikeCategory has increased compared to last week.",
                    transactions = thisWeekTxns.filter { it.category == topSpikeCategory },
                    highlightCategory = topSpikeCategory,
                    isPositiveTrend = false,
                    subText = "Spent ${formatAmount(currentAmt)} this wk vs ${formatAmount(pastAmt)} last wk"
                ))
            }
        }

        // 4. Monthly Pacing
        val thisMonthTxns = spendTxns.filter { it.date >= thisMonthStart }
        val thisMonthTotal = maxOf(0.0, thisMonthTxns.sumOf { it.amount } -
            refundTxns.filter { it.date >= thisMonthStart }.sumOf { it.amount })
        // To accurately compare, calculate what was spent *by this day* last month
        val lastMonthPacingCal = Calendar.getInstance()
        lastMonthPacingCal.timeInMillis = lastMonthStart
        lastMonthPacingCal.set(Calendar.DAY_OF_MONTH, currentDayOfMonth)
        val lastMonthPacingEnd = lastMonthPacingCal.timeInMillis
        val lastMonthPacingTxns = spendTxns.filter { it.date in lastMonthStart until lastMonthPacingEnd }
        val lastMonthPacingTotal = maxOf(0.0, lastMonthPacingTxns.sumOf { it.amount } -
            refundTxns.filter { it.date in lastMonthStart until lastMonthPacingEnd }.sumOf { it.amount })

        if (thisMonthTotal > 0 || lastMonthPacingTotal > 0) {
            val diff = thisMonthTotal - lastMonthPacingTotal
            
            val (desc, isPositive, sub) = when {
                diff > 0 -> Triple(
                    "You've spent ${formatAmount(thisMonthTotal)} this month.",
                    false,
                    "⚠️ Running ${formatAmount(diff)} higher than this time last month"
                )
                diff < 0 -> Triple(
                    "You've spent ${formatAmount(thisMonthTotal)} this month.",
                    true,
                    "✅ Great! You're ${formatAmount(Math.abs(diff))} below this time last month"
                )
                else -> Triple(
                    "You're currently at ${formatAmount(thisMonthTotal)} this month.",
                    null,
                    "Right on track with last month's pace"
                )
            }

            insights.add(InsightCard(
                title = "Monthly Overview",
                description = desc,
                transactions = thisMonthTxns,
                isPositiveTrend = isPositive,
                subText = sub
            ))
        }

        // 5. Cash Flow (income vs expense, savings rate) — uses the new Income data.
        val monthIncome = transactions.filter { it.isIncome && it.date >= thisMonthStart }.sumOf { it.amount }
        if (monthIncome > 0.0 || thisMonthTotal > 0.0) {
            val net = monthIncome - thisMonthTotal
            val savingsRate = if (monthIncome > 0) (net / monthIncome * 100).toInt() else 0
            val (desc, isPos, sub) = if (net >= 0) {
                Triple(
                    "You've saved ${formatAmount(net)} this month.",
                    true,
                    "💰 In ${formatAmount(monthIncome)} • Out ${formatAmount(thisMonthTotal)}" +
                        if (monthIncome > 0) " • Saving $savingsRate%" else ""
                )
            } else {
                Triple(
                    "You're ${formatAmount(-net)} over your income this month.",
                    false,
                    "⚠️ Spent ${formatAmount(thisMonthTotal)} vs income ${formatAmount(monthIncome)}"
                )
            }
            insights.add(InsightCard(
                title = "Cash Flow",
                description = desc,
                transactions = thisMonthTxns,
                isPositiveTrend = isPos,
                subText = sub
            ))
        }

        // 6. Unusual Spend — a charge far above the user's normal for that category.
        val catAverages = spendTxns.groupBy { it.category }
            .mapValues { entry -> entry.value.map { it.amount }.average() }
        val unusual = thisMonthTxns.filter { t ->
            val avg = catAverages[t.category] ?: 0.0
            avg > 0 && t.amount > avg * 2.5 && t.amount >= 2000
        }.maxByOrNull { it.amount }
        if (unusual != null) {
            insights.add(InsightCard(
                title = "Unusual Spend",
                description = "A ${formatAmount(unusual.amount)} charge at ${unusual.merchant} is well above your usual ${unusual.category} spend.",
                transactions = listOf(unusual),
                highlightCategory = unusual.category,
                isPositiveTrend = false,
                subText = "Tap to review"
            ))
        }

        // 7. New Merchant — first time spending somewhere this month.
        val priorMerchants = transactions
            .filter { it.date < thisMonthStart && it.isExpense }
            .map { it.merchant.lowercase() }
            .toSet()
        val newMerchantTxn = thisMonthTxns
            .filter { it.merchant.lowercase() != "unknown" && it.merchant.lowercase() !in priorMerchants }
            .maxByOrNull { it.amount }
        if (newMerchantTxn != null && newMerchantTxn.amount >= 500) {
            insights.add(InsightCard(
                title = "New Merchant",
                description = "First time spending at ${newMerchantTxn.merchant}.",
                transactions = listOf(newMerchantTxn),
                subText = "${formatAmount(newMerchantTxn.amount)} • ${newMerchantTxn.category}"
            ))
        }

        return insights
    }

    /**
     * Subscription-aware insights: month-end forecast, recurring price hikes, and
     * possibly-unused subscriptions. Kept separate so callers can pass in the
     * subscriptions list (which generateInsights doesn't take).
     */
    fun recurringInsights(
        transactions: List<Transaction>,
        subscriptions: List<com.example.smsaggregator.data.Subscription>
    ): List<InsightCard> {
        val cards = mutableListOf<InsightCard>()
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val thisMonthStart = getStartOfMonth(0)
        val dayMs = 24L * 60 * 60 * 1000

        // 1. Month-end forecast — linear projection of net spend so far.
        val spentSoFar = transactions.filter { it.isExpense && it.date >= thisMonthStart }.sumOf { it.amount } -
            transactions.filter { it.isRefund && it.date >= thisMonthStart }.sumOf { it.amount }
        if (spentSoFar > 0 && currentDay >= 3) {
            val projected = (spentSoFar / currentDay) * daysInMonth
            val monthEnd = thisMonthStart + daysInMonth.toLong() * dayMs
            val upcoming = subscriptions.filter { it.nextExpectedDate in now..monthEnd }.sumOf { it.amount }
            cards.add(InsightCard(
                title = "Month-End Forecast",
                description = "At this pace you'll spend about ${formatAmount(projected)} this month.",
                transactions = emptyList(),
                isPositiveTrend = null,
                subText = if (upcoming > 0) "Plus ~${formatAmount(upcoming)} in upcoming subscriptions" else "Based on your spending pace so far"
            ))
        }

        // 2. Recurring price hikes (max 2).
        val subMerchants = subscriptions.map { it.merchant.lowercase() }.toSet()
        val byMerchant = transactions.filter { it.isExpense }.groupBy { it.merchant }
        var hikes = 0
        for ((merchant, list) in byMerchant) {
            if (hikes >= 2) break
            if (merchant.lowercase() !in subMerchants || list.size < 2) continue
            val sorted = list.sortedBy { it.date }
            val lastAmt = sorted.last().amount
            val prevDifferent = sorted.dropLast(1).map { it.amount }.lastOrNull { it != lastAmt } ?: continue
            if (lastAmt > prevDifferent * 1.05 && lastAmt - prevDifferent >= 10) {
                cards.add(InsightCard(
                    title = "Price Increase",
                    description = "$merchant went from ${formatAmount(prevDifferent)} to ${formatAmount(lastAmt)}.",
                    transactions = sorted.sortedByDescending { it.date }.take(3),
                    highlightCategory = sorted.last().category,
                    isPositiveTrend = false,
                    subText = "A recurring charge increased"
                ))
                hikes++
            }
        }

        // 3. Possibly-unused subscriptions (no charge for >1.6× the cycle), max 2.
        var unused = 0
        for (sub in subscriptions) {
            if (unused >= 2) break
            val cycle = if (sub.frequencyDays > 0) sub.frequencyDays else 30
            val daysSince = (now - sub.lastDate) / dayMs
            if (daysSince > cycle * 1.6) {
                cards.add(InsightCard(
                    title = "Unused Subscription?",
                    description = "No ${sub.merchant} charge in $daysSince days.",
                    transactions = emptyList(),
                    isPositiveTrend = null,
                    subText = "Cancelled, paused, or worth reviewing"
                ))
                unused++
            }
        }

        return cards
    }

    data class SourceBreakdown(
        val sourceName: String,
        val totalSpent: Double,
        val percentage: Float,
        val transactions: List<Transaction>
    )

    fun calculateSourceBreakdown(transactions: List<Transaction>): List<SourceBreakdown> {
        val thisMonthStart = getStartOfMonth(0)
        
        // Filter only this month's debits
        val validTxns = transactions.filter { it.date >= thisMonthStart && it.type.name == "DEBIT" }
        if (validTxns.isEmpty()) return emptyList()

        val grandTotal = validTxns.sumOf { it.amount }
        if (grandTotal <= 0) return emptyList()

        // Group by Source (e.g., Credit Card, UPI, Wallet)
        val sortedGroups = validTxns.groupBy { 
            if (it.source.isBlank() || it.source == "Unknown") "Bank Account" else it.source 
        }.toList().sortedByDescending { it.second.sumOf { t -> t.amount } }

        return sortedGroups.map { (source, txns) ->
            val amt = txns.sumOf { it.amount }
            SourceBreakdown(
                sourceName = source,
                totalSpent = amt,
                percentage = ((amt / grandTotal) * 100).toFloat(),
                transactions = txns
            )
        }
    }
}
