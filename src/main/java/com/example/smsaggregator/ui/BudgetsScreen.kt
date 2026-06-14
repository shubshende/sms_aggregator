package com.example.smsaggregator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smsaggregator.data.Budget
import com.example.smsaggregator.data.Transaction
import com.example.smsaggregator.data.isExpense
import com.example.smsaggregator.data.isRefund
import com.example.smsaggregator.ui.theme.CatColor
import com.example.smsaggregator.ui.theme.Dark_SurfCont
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BudgetsScreen(
    viewModel: TransactionViewModel,
    transactions: List<Transaction>,
    budgets: List<Budget>,
    modifier: Modifier = Modifier,
    onTransactionClick: (Transaction) -> Unit = {}
) {
    // ── Month selector state ──
    var selectedMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var selectedYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    val monthNames = arrayOf("January","February","March","April","May","June","July","August","September","October","November","December")

    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, selectedYear); cal.set(Calendar.MONTH, selectedMonth)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val nowCal = Calendar.getInstance()
    val isCurrentMonth = selectedMonth == nowCal.get(Calendar.MONTH) && selectedYear == nowCal.get(Calendar.YEAR)
    val curDay = if (isCurrentMonth) nowCal.get(Calendar.DAY_OF_MONTH) else daysInMonth
    val daysLeft = if (isCurrentMonth) daysInMonth - curDay else 0

    val fmt = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 } }

    val monthRange = remember(selectedYear, selectedMonth) { com.example.smsaggregator.util.DateUtils.monthRange(selectedYear, selectedMonth) }
    val monthTxns = remember(transactions, monthRange) {
        transactions.filter {
            !it.isIgnored && it.date >= monthRange.first && it.date < monthRange.second
        }
    }

    val allSplits by viewModel.allSplits.collectAsState()
    val splitsByTxn = remember(allSplits) { allSplits.groupBy { it.transactionId } }

    val catSpend = remember(monthTxns, allSplits) {
        val m = mutableMapOf<String, Double>()
        val c = mutableMapOf<String, Int>()
        // Only money-out rows count as spending.
        monthTxns.filter { it.isExpense }.forEach { t ->
            val tSplits = splitsByTxn[t.id].orEmpty()
            if (tSplits.isNotEmpty()) {
                tSplits.filter { it.category != "Shared/Other" }.forEach { s ->
                    val cat = if (s.category == "My Expense") t.category else s.category
                    m[cat] = (m[cat] ?: 0.0) + s.amount
                    c[cat] = (c[cat] ?: 0) + 1
                }
            } else {
                m[t.category] = (m[t.category] ?: 0.0) + t.amount
                c[t.category] = (c[t.category] ?: 0) + 1
            }
        }
        // Net refunds/cashback against their category (clamped at zero).
        monthTxns.filter { it.isRefund }.forEach { t ->
            if (m.containsKey(t.category)) {
                m[t.category] = maxOf(0.0, (m[t.category] ?: 0.0) - t.amount)
            }
        }
        m to c
    }
    val spendMap = catSpend.first
    val countMap = catSpend.second

    val cats = remember(spendMap, budgets) {
        val allCats = (spendMap.keys + budgets.map { it.category }).distinct()
        allCats.map { cat ->
            val spent = spendMap[cat] ?: 0.0
            val budgetObj = budgets.find { it.category == cat }
            val limit = budgetObj?.monthlyLimit ?: 0.0
            val rollover = if (isCurrentMonth) budgetObj?.rolloverAmount ?: 0.0 else 0.0
            CatSlice(cat, spent, limit + rollover, CatColor.tone(cat), CatColor.bg(cat), CatColor.icon(cat), countMap[cat] ?: 0)
        }.sortedByDescending { it.spent }
    }

    val totalSpent = cats.sumOf { it.spent }
    val totalBudget = cats.sumOf { it.budget }.let { if (it <= 0.0) totalSpent * 1.2 else it }
    val overallPct = if (totalBudget > 0) (totalSpent / totalBudget * 100).toInt().coerceIn(0, 200) else 0
    val paceExpected = (totalBudget * curDay / daysInMonth).toInt()

    // ── Edit budget dialog ──
    var editingCat by remember { mutableStateOf<String?>(null) }
    var editValue by remember { mutableStateOf("") }

    if (editingCat != null) {
        AlertDialog(
            onDismissRequest = { editingCat = null },
            title = { Text("Set Budget", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(editingCat!!, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        editValue, { editValue = it },
                        label = { Text("Monthly limit (₹)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { val v = editValue.toDoubleOrNull() ?: 0.0; viewModel.setBudget(editingCat!!, v); editingCat = null },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingCat = null }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ── Category drill-down ──
    var expandedCat by remember { mutableStateOf<String?>(null) }

    // Open a category's transaction list when requested from elsewhere (e.g. Home ring tap).
    val pendingCategory by viewModel.pendingCategory.collectAsState()
    LaunchedEffect(pendingCategory) {
        val cat = pendingCategory
        if (cat != null) {
            expandedCat = cat
            viewModel.clearPendingCategory()
        }
    }
    val expandedTxns = remember(expandedCat, monthTxns, allSplits) {
        if (expandedCat == null) emptyList()
        else monthTxns.filter { t ->
            val tSplits = splitsByTxn[t.id].orEmpty()
            if (tSplits.isNotEmpty()) {
                tSplits.any { (if (it.category == "My Expense") t.category else it.category) == expandedCat && it.category != "Shared/Other" }
            } else {
                t.category == expandedCat
            }
        }.sortedByDescending { it.date }
    }

    if (expandedCat != null) {
        // Handle system back button
        BackHandler { expandedCat = null }
        // Category transaction list overlay
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val catTotal = remember(expandedCat, monthTxns, allSplits) {
                var sum = 0.0
                monthTxns.forEach { t ->
                    if (!t.isIgnored) {
                        val tSplits = splitsByTxn[t.id].orEmpty()
                        if (tSplits.isNotEmpty()) {
                            tSplits.filter { it.category != "Shared/Other" }.forEach { s ->
                                val cat = if (s.category == "My Expense") t.category else s.category
                                if (cat == expandedCat) sum += s.amount
                            }
                        } else if (t.category == expandedCat) {
                            sum += t.amount
                        }
                    }
                }
                sum
            }
            Row(Modifier.fillMaxWidth().padding(4.dp, 8.dp, 4.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { expandedCat = null }) {
                    Icon(Icons.Outlined.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(expandedCat!!, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(fmt.format(catTotal), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 16.dp))
            }
            
            val catBudget = budgets.find { it.category == expandedCat }?.monthlyLimit ?: 0.0
            if (catBudget > 0) {
                val pct = (catTotal / catBudget).toFloat().coerceIn(0f, 1f)
                Column(Modifier.padding(16.dp, 8.dp)) {
                    Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                        Box(Modifier.fillMaxWidth(pct).height(8.dp).background(CatColor.tone(expandedCat!!), RoundedCornerShape(4.dp)))
                    }
                    Text("${fmt.format(catTotal)} of ${fmt.format(catBudget)} budget", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(expandedTxns, key = { it.id }) { t ->
                    val tSplits = splitsByTxn[t.id].orEmpty()
                    val displayAmount = if (tSplits.isNotEmpty()) {
                        tSplits.filter { (if (it.category == "My Expense") t.category else it.category) == expandedCat && it.category != "Shared/Other" }.sumOf { it.amount }
                    } else {
                        t.amount
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { onTransactionClick(t) }.padding(16.dp, 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(40.dp).background(CatColor.bg(t.category), RoundedCornerShape(12.dp)), Alignment.Center) {
                            Box(Modifier.size(16.dp).background(CatColor.tone(t.category), CircleShape))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(t.merchant, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(t.date)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            fmt.format(displayAmount), 
                            fontSize = 14.sp, 
                            fontWeight = FontWeight.Medium, 
                            color = if (t.isIgnored) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (t.isIgnored) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        return
    }

    // ── Main Budgets Screen ──
    LazyColumn(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentPadding = PaddingValues(bottom = 32.dp)) {
        // Header
        item {
            Row(Modifier.fillMaxWidth().padding(24.dp, 16.dp, 16.dp, 0.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Budgets", fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurface, letterSpacing = (-0.6).sp)
                IconButton(onClick = { viewModel.applyBudgetRollover() }) {
                    Icon(Icons.Outlined.History, "Apply Rollover", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // ── Month Selector ──
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                IconButton(onClick = { if (selectedMonth == 0) { selectedMonth = 11; selectedYear-- } else selectedMonth-- }) {
                    Icon(Icons.Outlined.ChevronLeft, "Prev", tint = MaterialTheme.colorScheme.onSurface)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${monthNames[selectedMonth]} $selectedYear", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    if (isCurrentMonth) Text("Current month", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { if (selectedMonth == 11) { selectedMonth = 0; selectedYear++ } else selectedMonth++ }) {
                    Icon(Icons.Outlined.ChevronRight, "Next", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Overall Card
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp), RoundedCornerShape(24.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("OVERALL", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
                        Text(
                            if (isCurrentMonth) "$overallPct% used · $daysLeft days left" else "$overallPct% used",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                        Text(fmt.format(totalSpent), fontSize = 30.sp, color = MaterialTheme.colorScheme.onSurface, letterSpacing = (-0.6).sp)
                        Text(" / ${fmt.format(totalBudget)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp))) {
                        val fill = (overallPct / 100f).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth(fill).height(10.dp).background(if (overallPct > 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp)))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), Arrangement.SpaceBetween) {
                        Text("On pace: ${fmt.format(paceExpected)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${fmt.format((totalBudget - totalSpent).coerceAtLeast(0.0))} left", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Category header + set budget button
        item {
            Row(Modifier.fillMaxWidth().padding(24.dp, 24.dp, 16.dp, 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Categories", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                TextButton(
                    onClick = { editingCat = "Overall"; editValue = totalBudget.toInt().toString() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Outlined.Edit, "Set", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Set Budget", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Category Cards (clickable to drill down)
        items(cats) { cat ->
            val pct = if (cat.budget > 0) (cat.spent / cat.budget).toFloat() else 0f
            val over = pct > 1f
            val barColor = if (over) MaterialTheme.colorScheme.error else cat.color
            val animPct by animateFloatAsState(pct.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing), label = "bp")

            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable { expandedCat = cat.name },
                RoundedCornerShape(20.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(cat.bg, RoundedCornerShape(12.dp)), Alignment.Center) {
                            Box(Modifier.size(16.dp).background(cat.color, CircleShape))
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f, false)) {
                                    Text(cat.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val rolloverVal = budgets.find { it.category == cat.name }?.rolloverAmount ?: 0.0
                                    if (rolloverVal > 0 && isCurrentMonth) {
                                        Text("Incl. ₹${rolloverVal.toInt()} rollover", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(fmt.format(cat.spent), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                    if (cat.budget > 0) Text(" / ${fmt.format(cat.budget)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 2.dp), Arrangement.SpaceBetween) {
                                Text("${cat.count} txns", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (cat.budget > 0) Text("${(pct * 100).toInt()}%", fontSize = 11.sp, color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (over) FontWeight.Bold else FontWeight.Normal)
                                    Spacer(Modifier.width(4.dp))
                                    // Edit budget icon
                                    Icon(
                                        Icons.Outlined.Edit, "Edit budget",
                                        Modifier.size(14.dp).clickable { editingCat = cat.name; editValue = cat.budget.toInt().toString() },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                        // Chevron to drill down
                        Icon(Icons.Outlined.ChevronRight, "View", Modifier.padding(start = 4.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    if (cat.budget > 0) {
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                            Box(Modifier.fillMaxWidth(animPct).height(6.dp).background(barColor, RoundedCornerShape(3.dp)))
                        }
                    }
                }
            }
        }
    }
}
