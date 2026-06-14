package com.example.smsaggregator.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smsaggregator.data.Transaction
import com.example.smsaggregator.data.isExpense
import com.example.smsaggregator.data.isRefund
import com.example.smsaggregator.logic.InsightsEngine
import com.example.smsaggregator.ui.theme.CatColor
import com.example.smsaggregator.ui.theme.Dark_SurfCont
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: TransactionViewModel,
    transactions: List<Transaction>,
    modifier: Modifier = Modifier
) {
    val fmt = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 } }
    val cal = Calendar.getInstance()
    val curMonth = cal.get(Calendar.MONTH)
    val curYear = cal.get(Calendar.YEAR)

    var selectedSource by remember { mutableStateOf("All") }
    var showRangePicker by remember { mutableStateOf(false) }
    var dateRange by remember { mutableStateOf<Pair<Long, Long>?>(null) } // start to end
    val sources = remember(transactions) { listOf("All") + transactions.map { it.source }.distinct().sorted() }

    val filteredTxns = remember(transactions, selectedSource) {
        if (selectedSource == "All") transactions else transactions.filter { it.source == selectedSource }
    }

    val allSplits by viewModel.allSplits.collectAsState()
    val splitsByTxn = remember(allSplits) { allSplits.groupBy { it.transactionId } }

    val monthlyTotals = remember(filteredTxns, allSplits) {
        (0..5).reversed().map { ago ->
            val c = Calendar.getInstance(); c.add(Calendar.MONTH, -ago)
            val m = c.get(Calendar.MONTH); val y = c.get(Calendar.YEAR)
            val (mStart, mEnd) = com.example.smsaggregator.util.DateUtils.monthRange(y, m)
            val monthRows = filteredTxns.filter { it.date >= mStart && it.date < mEnd }
            val spent = monthRows.filter { it.isExpense }.sumOf { t ->
                val tSplits = splitsByTxn[t.id].orEmpty()
                if (tSplits.isNotEmpty()) {
                    tSplits.filter { it.category != "Shared/Other" }.sumOf { it.amount }
                } else {
                    t.amount
                }
            }
            val refunds = monthRows.filter { it.isRefund }.sumOf { it.amount }
            val total = maxOf(0.0, spent - refunds)
            Triple(m, y, total)
        }
    }
    val monthNames = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    val maxBar = monthlyTotals.maxOf { it.third }.let { if (it <= 0.0) 1.0 else it }

    val thisMonthTotal = monthlyTotals.lastOrNull()?.third ?: 0.0
    val lastMonthTotal = if (monthlyTotals.size >= 2) monthlyTotals[monthlyTotals.size - 2].third else 0.0
    val delta = thisMonthTotal - lastMonthTotal
    val deltaPct = if (lastMonthTotal > 0) ((delta / lastMonthTotal) * 100).toInt() else 0

    val curRange = remember(curYear, curMonth) { com.example.smsaggregator.util.DateUtils.monthRange(curYear, curMonth) }
    val monthTxns = remember(filteredTxns, dateRange, curRange) {
        if (dateRange != null) {
            filteredTxns.filter { !it.isIgnored && it.date in dateRange!!.first..dateRange!!.second }
        } else {
            filteredTxns.filter { !it.isIgnored && it.date >= curRange.first && it.date < curRange.second }
        }
    }
    val catShare = remember(monthTxns, allSplits) {
        val m = mutableMapOf<String, Double>()
        monthTxns.filter { it.isExpense }.forEach { t ->
            val tSplits = splitsByTxn[t.id].orEmpty()
            if (tSplits.isNotEmpty()) {
                tSplits.filter { it.category != "Shared/Other" }.forEach { s ->
                    val cat = if (s.category == "My Expense") t.category else s.category
                    m[cat] = (m[cat] ?: 0.0) + s.amount
                }
            } else {
                m[t.category] = (m[t.category] ?: 0.0) + t.amount
            }
        }
        // Net refunds against their category.
        monthTxns.filter { it.isRefund }.forEach { t ->
            if (m.containsKey(t.category)) {
                m[t.category] = maxOf(0.0, (m[t.category] ?: 0.0) - t.amount)
            }
        }
        m.entries.filter { it.value > 0 }.sortedByDescending { it.value }
    }

    val topMerchants = remember(monthTxns, allSplits) {
        val m = mutableMapOf<String, Double>()
        monthTxns.filter { it.isExpense }.forEach { t ->
            val tSplits = splitsByTxn[t.id].orEmpty()
            val myAmount = if (tSplits.isNotEmpty()) {
                tSplits.filter { it.category != "Shared/Other" }.sumOf { it.amount }
            } else {
                t.amount
            }
            m[t.merchant] = (m[t.merchant] ?: 0.0) + myAmount
        }
        m.entries.sortedByDescending { it.value }.take(4)
    }

    val subscriptions by viewModel.subscriptions.collectAsState()
    val insights = remember(filteredTxns, subscriptions) {
        val nonIgnored = filteredTxns.filter { !it.isIgnored }
        InsightsEngine.generateInsights(nonIgnored) +
            InsightsEngine.recurringInsights(nonIgnored, subscriptions)
    }
    val topInsight = insights.firstOrNull()

    LazyColumn(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Column(Modifier.padding(24.dp, 16.dp, 24.dp, 4.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(if (dateRange == null) "${monthNames[curMonth]} $curYear" else "Custom Range", fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurface, letterSpacing = (-0.6).sp)
                    IconButton(onClick = { showRangePicker = true }) {
                        Icon(Icons.Outlined.DateRange, "Filter Date")
                    }
                }
                Text(if (dateRange == null) "Monthly report" else "${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(dateRange!!.first))} - ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(dateRange!!.second))}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // View Toggles (Monthly / Yearly)
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = dateRange == null,
                    onClick = { dateRange = null },
                    label = { Text("Monthly") },
                    shape = RoundedCornerShape(12.dp)
                )
                FilterChip(
                    selected = dateRange != null && run {
                        val c = Calendar.getInstance(); c.timeInMillis = dateRange!!.first
                        val c2 = Calendar.getInstance(); c2.timeInMillis = dateRange!!.second
                        c.get(Calendar.DAY_OF_YEAR) == 1 && c2.get(Calendar.MONTH) == 11 && c2.get(Calendar.DAY_OF_MONTH) == 31
                    },
                    onClick = {
                        val c = Calendar.getInstance()
                        c.set(Calendar.MONTH, 0)
                        c.set(Calendar.DAY_OF_MONTH, 1)
                        c.set(Calendar.HOUR_OF_DAY, 0)
                        c.set(Calendar.MINUTE, 0)
                        c.set(Calendar.SECOND, 0)
                        val start = c.timeInMillis
                        c.set(Calendar.MONTH, 11)
                        c.set(Calendar.DAY_OF_MONTH, 31)
                        c.set(Calendar.HOUR_OF_DAY, 23)
                        c.set(Calendar.MINUTE, 59)
                        c.set(Calendar.SECOND, 59)
                        val end = c.timeInMillis
                        dateRange = start to end
                    },
                    label = { Text("Yearly") },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Source Filter Chips
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sources.forEach { source ->
                    FilterChip(
                        selected = selectedSource == source,
                        onClick = { selectedSource = source },
                        label = { Text(source) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // Hero Total
        item {
            Column(Modifier.padding(24.dp, 20.dp, 24.dp, 0.dp)) {
                Text("TOTAL SPENT", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
                Text(fmt.format(thisMonthTotal), fontSize = 44.sp, color = MaterialTheme.colorScheme.onSurface, letterSpacing = (-1.5).sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 4.dp))
                if (deltaPct != 0) {
                    val up = delta > 0
                    Box(
                        Modifier.padding(top = 8.dp).background(if (up) Color(0xFF3A1F1F) else Color(0xFF1A3228), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (up) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
                                "trend", tint = if (up) Color(0xFFF0A098) else Color(0xFF7CD0A0),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${Math.abs(deltaPct)}% vs last month",
                                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                color = if (up) Color(0xFFF0A098) else Color(0xFF7CD0A0)
                            )
                        }
                    }
                }
            }
        }

        // Upcoming Bills
        if (subscriptions.isNotEmpty()) {
            item {
                Column(Modifier.padding(16.dp, 28.dp, 16.dp, 0.dp)) {
                    Text("Upcoming Bills", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(subscriptions.size) { idx ->
                            val sub = subscriptions[idx]
                            Card(
                                Modifier.width(200.dp),
                                RoundedCornerShape(20.dp),
                                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    // Delete button in top right
                                    IconButton(
                                        onClick = { viewModel.deleteSubscription(sub) },
                                        modifier = Modifier.align(Alignment.TopEnd).size(32.dp).padding(4.dp)
                                    ) {
                                        Icon(androidx.compose.material.icons.Icons.Outlined.Close, "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                    }

                                    Column(Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(32.dp).background(CatColor.bg(sub.category), CircleShape), Alignment.Center) {
                                                Text(CatColor.icon(sub.category), fontSize = 14.sp)
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Text(sub.merchant, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 20.dp))
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        Text(fmt.format(sub.amount), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        val nextDate = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(sub.nextExpectedDate))
                                        Text("Next: $nextDate", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6-Month Bar Chart
        item {
            Column(Modifier.padding(16.dp, 32.dp, 16.dp, 0.dp)) {
                Text("6-Month Trend", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
                val barBgColor = MaterialTheme.colorScheme.surfaceVariant
                Canvas(Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 8.dp)) {
                    val barW = size.width / monthlyTotals.size - 12f
                    monthlyTotals.forEachIndexed { i, (_, _, total) ->
                        val h = ((total / maxBar) * (size.height - 24f)).toFloat()
                        val x = i * (barW + 12f) + 6f
                        val isCurrent = i == monthlyTotals.lastIndex
                        drawRoundRect(
                            if (isCurrent) Color(0xFFE8C37A) else barBgColor,
                            Offset(x, size.height - h - 12f),
                            Size(barW, h),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), Arrangement.SpaceBetween) {
                    monthlyTotals.forEach { (m, _, _) -> Text(monthNames[m], fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }

        // Category Share
        if (catShare.isNotEmpty()) {
            item {
                Column(Modifier.padding(16.dp, 28.dp, 16.dp, 0.dp)) {
                    Text("Category Split", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
                    Canvas(Modifier.fillMaxWidth().height(12.dp).padding(horizontal = 8.dp)) {
                        val total = catShare.sumOf { it.value }.let { if (it <= 0.0) 1.0 else it }
                        var x = 0f
                        catShare.forEach { (cat, spent) ->
                            val w = ((spent / total) * size.width).toFloat()
                            drawRoundRect(CatColor.tone(cat), Offset(x, 0f), Size(w - 2f, size.height), cornerRadius = CornerRadius(4f, 4f))
                            x += w
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    catShare.forEach { (cat, spent) ->
                        val pct = if (thisMonthTotal > 0) Math.round(spent / thisMonthTotal * 100) else 0
                        Row(Modifier.padding(vertical = 3.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(CatColor.tone(cat), RoundedCornerShape(2.dp)))
                            Text(cat, Modifier.padding(start = 8.dp).weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$pct%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                            Text(fmt.format(spent), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        // Top Merchants
        if (topMerchants.isNotEmpty()) {
            item {
                Column(Modifier.padding(16.dp, 28.dp, 16.dp, 0.dp)) {
                    Text("Top Merchants", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column {
                            topMerchants.forEachIndexed { idx, entry ->
                                Row(Modifier.fillMaxWidth().padding(14.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(String.format("%02d", idx + 1), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                                    Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)), Alignment.Center) {
                                        Text(entry.key.take(1).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(entry.key, Modifier.weight(1f).padding(horizontal = 12.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(fmt.format(entry.value), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                }
                                if (idx < topMerchants.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 14.dp))
                            }
                        }
                    }
                }
            }
        }

        // AI Insight Card
        if (topInsight != null) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp, 28.dp, 16.dp, 0.dp),
                    RoundedCornerShape(20.dp),
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Lightbulb, "Insight", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(topInsight.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(topInsight.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), modifier = Modifier.padding(top = 4.dp), lineHeight = 18.sp)
                            if (topInsight.subText != null) {
                                Text(topInsight.subText!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f), modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (showRangePicker) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = state.selectedStartDateMillis
                    val end = state.selectedEndDateMillis
                    if (start != null && end != null) {
                        dateRange = start to end
                    } else {
                        dateRange = null
                    }
                    showRangePicker = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    dateRange = null
                    showRangePicker = false 
                }) { Text("Reset") }
            }
        ) {
            DateRangePicker(state = state, modifier = Modifier.height(450.dp))
        }
    }
}
