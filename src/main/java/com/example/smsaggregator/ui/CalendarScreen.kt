package com.example.smsaggregator.ui

import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smsaggregator.data.Transaction
import com.example.smsaggregator.data.isExpense
import com.example.smsaggregator.data.isIncome
import com.example.smsaggregator.data.isRefund
import com.example.smsaggregator.ui.theme.CatColor
import com.example.smsaggregator.ui.theme.Dark_SurfCont
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun CalendarScreen(
    viewModel: TransactionViewModel,
    transactions: List<Transaction>,
    onTransactionClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    
    
    
    val fmt = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 } }
    val allSplits by viewModel.allSplits.collectAsState()
    val splitsByTxn = remember(allSplits) { allSplits.groupBy { it.transactionId } }
    val bills by viewModel.bills.collectAsState()

    val cal = remember { Calendar.getInstance() }
    cal.set(Calendar.YEAR, viewModel.calendarYear); cal.set(Calendar.MONTH, viewModel.calendarMonth); cal.set(Calendar.DAY_OF_MONTH, 1)

    val nowCal = Calendar.getInstance()
    val today = nowCal.get(Calendar.DAY_OF_MONTH)
    val monthRange = remember(viewModel.calendarYear, viewModel.calendarMonth) { com.example.smsaggregator.util.DateUtils.monthRange(viewModel.calendarYear, viewModel.calendarMonth) }
    val monthTxns = remember(transactions, monthRange) {
        transactions.filter { it.date >= monthRange.first && it.date < monthRange.second }
    }

    val monthBills = remember(bills, monthRange) {
        bills.filter { it.dueDate >= monthRange.first && it.dueDate < monthRange.second }
    }

    val monthNames = arrayOf("January","February","March","April","May","June","July","August","September","October","November","December")

    val dayTxns = remember(viewModel.calendarDay, monthTxns) {
        if (viewModel.calendarDay == null) emptyList()
        else monthTxns.filter { val c = Calendar.getInstance(); c.timeInMillis = it.date; c.get(Calendar.DAY_OF_MONTH) == viewModel.calendarDay }
    }

    val dayBills = remember(viewModel.calendarDay, monthBills) {
        if (viewModel.calendarDay == null) emptyList()
        else monthBills.filter { val c = Calendar.getInstance(); c.timeInMillis = it.dueDate; c.get(Calendar.DAY_OF_MONTH) == viewModel.calendarDay }
    }

    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val heatColors = if (isDarkTheme) {
        listOf(Color(0xFF2A2620), Color(0xFF3A2E1C), Color(0xFF5C4418), Color(0xFF8A6B24), Color(0xFFBF9238), Color(0xFFE8C37A))
    } else {
        listOf(Color(0xFFF3EEE3), Color(0xFFE6DFCD), Color(0xFFDCD5C3), Color(0xFFC0AB87), Color(0xFFA57C42), Color(0xFF8A5A1C))
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { dragAccumulator = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAccumulator != Float.MAX_VALUE && dragAccumulator != Float.MIN_VALUE) {
                            dragAccumulator += dragAmount
                            if (dragAccumulator > 150f) {
                                if (viewModel.calendarMonth == 0) { viewModel.calendarMonth = 11; viewModel.calendarYear-- } else viewModel.calendarMonth--
                                viewModel.calendarDay = null
                                dragAccumulator = Float.MAX_VALUE
                            } else if (dragAccumulator < -150f) {
                                if (viewModel.calendarMonth == 11) { viewModel.calendarMonth = 0; viewModel.calendarYear++ } else viewModel.calendarMonth++
                                viewModel.calendarDay = null
                                dragAccumulator = Float.MIN_VALUE
                            }
                        }
                    }
                )
            }, 
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            androidx.compose.animation.AnimatedContent(
                targetState = Pair(viewModel.calendarMonth, viewModel.calendarYear),
                transitionSpec = {
                    androidx.compose.animation.fadeIn(animationSpec = tween(300)) togetherWith androidx.compose.animation.fadeOut(animationSpec = tween(300))
                },
                label = "calendarAnim"
            ) { (cMonth, cYear) ->
                val cCal = remember(cMonth, cYear) { Calendar.getInstance().apply { set(Calendar.YEAR, cYear); set(Calendar.MONTH, cMonth); set(Calendar.DAY_OF_MONTH, 1) } }
                val cDaysInMonth = cCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val cFirstDow = cCal.get(Calendar.DAY_OF_WEEK)
                
                val cMonthRange = remember(cMonth, cYear) { com.example.smsaggregator.util.DateUtils.monthRange(cYear, cMonth) }
                val cMonthTxns = remember(transactions, cMonthRange) {
                    transactions.filter { it.date >= cMonthRange.first && it.date < cMonthRange.second }
                }

                val cDailySpend = remember(cMonthTxns, allSplits) {
                    val arr = DoubleArray(cDaysInMonth + 1)
                    cMonthTxns.filter { it.isExpense }.forEach { t ->
                        val tSplits = splitsByTxn[t.id].orEmpty()
                        val amount = if (tSplits.isNotEmpty()) {
                            tSplits.filter { it.category != "Shared/Other" }.sumOf { it.amount }
                        } else {
                            t.amount
                        }
                        val cc = Calendar.getInstance(); cc.timeInMillis = t.date
                        arr[cc.get(Calendar.DAY_OF_MONTH)] += amount
                    }
                    // Net refunds against the day they land on (clamped at zero per day).
                    cMonthTxns.filter { it.isRefund }.forEach { t ->
                        val cc = Calendar.getInstance(); cc.timeInMillis = t.date
                        val d = cc.get(Calendar.DAY_OF_MONTH)
                        arr[d] = maxOf(0.0, arr[d] - t.amount)
                    }
                    arr
                }

                val cDailyBills = remember(monthBills) {
                    val arr = BooleanArray(cDaysInMonth + 1)
                    monthBills.forEach { b ->
                        val cc = Calendar.getInstance(); cc.timeInMillis = b.dueDate
                        if (cc.get(Calendar.MONTH) == cMonth && cc.get(Calendar.YEAR) == cYear) {
                            arr[cc.get(Calendar.DAY_OF_MONTH)] = true
                        }
                    }
                    arr
                }

                val cMaxSpend = cDailySpend.max().let { if (it <= 0.0) 1.0 else it }
                val cTotalSpent = cDailySpend.sum()
                val cActiveDays = cDailySpend.count { it > 0 }
                val cAvgSpend = if (cActiveDays > 0) cTotalSpent / cActiveDays else 0.0
                val cTopDay = cDailySpend.indices.maxByOrNull { cDailySpend[it] } ?: 0
                val cIsCurrentMonth = cMonth == nowCal.get(Calendar.MONTH) && cYear == nowCal.get(Calendar.YEAR)
                val cNoSpendDays = (if (cIsCurrentMonth) today else cDaysInMonth) - cActiveDays

                Column {
                    Column(Modifier.padding(24.dp, 16.dp, 24.dp, 4.dp)) {
                        Text("Calendar", fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurface, letterSpacing = (-0.6).sp)
                        Text("Your spending rhythm", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }

                    // Month Switcher
                    Row(Modifier.fillMaxWidth().padding(24.dp, 20.dp, 24.dp, 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        IconButton(onClick = { if (viewModel.calendarMonth == 0) { viewModel.calendarMonth = 11; viewModel.calendarYear-- } else viewModel.calendarMonth--; viewModel.calendarDay = null }) {
                            Icon(Icons.Outlined.ChevronLeft, "Prev", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${monthNames[cMonth]} $cYear", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text(fmt.format(cTotalSpent) + " total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { if (viewModel.calendarMonth == 11) { viewModel.calendarMonth = 0; viewModel.calendarYear++ } else viewModel.calendarMonth++; viewModel.calendarDay = null }) {
                            Icon(Icons.Outlined.ChevronRight, "Next", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Day Headers
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), Arrangement.SpaceEvenly) {
                        listOf("S","M","T","W","T","F","S").forEach {
                            Text(it, Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }

                    // Heatmap Grid
                    val totalRows = ((cDaysInMonth + cFirstDow - 1 + 6) / 7)
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        for (row in 0 until totalRows) {
                            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), Arrangement.SpaceEvenly) {
                                for (col in 0..6) {
                                    val day = row * 7 + col - cFirstDow + 2
                                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                                        if (day in 1..cDaysInMonth) {
                                            val spend = cDailySpend[day]
                                            val hasBill = cDailyBills[day]
                                            val f = if (spend > 0) (Math.log10(spend) / Math.log10(cMaxSpend)).coerceIn(0.0, 1.0) else 0.0
                                            val bucket = when { spend == 0.0 -> 0; f < 0.3 -> 1; f < 0.55 -> 2; f < 0.75 -> 3; f < 0.9 -> 4; else -> 5 }
                                            val isToday = cIsCurrentMonth && day == today
                                            val isSel = day == viewModel.calendarDay
                                            val textColor = if (isDarkTheme) {
                                                    if (bucket >= 3) Color(0xFF1D1A14) else MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    if (bucket >= 4) Color(0xFFFFFFFF) else MaterialTheme.colorScheme.onSurface
                                                }
                                            val amountColor = if (isDarkTheme) {
                                                if (bucket >= 3) Color(0xFF1D1A14).copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                if (bucket >= 4) Color(0xFFFFFFFF).copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                            
                                            Box(
                                                Modifier.fillMaxSize()
                                                    .background(heatColors[bucket], RoundedCornerShape(10.dp))
                                                    .border(
                                                        width = if (isToday) 1.5.dp else if (isSel) 1.dp else if (hasBill) 1.5.dp else 0.dp, 
                                                        color = if (isToday) MaterialTheme.colorScheme.onSurface else if (isSel) MaterialTheme.colorScheme.primary else if (hasBill) Color(0xFFEE5253) else Color.Transparent, 
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable { viewModel.calendarDay = if (viewModel.calendarDay == day) null else day }
                                                    .padding(5.dp)
                                            ) {
                                                Text(day.toString(), Modifier.align(Alignment.TopStart), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = textColor)
                                                if (spend > 0) Text(if (spend >= 1000) "${(spend/1000).toInt()}k" else spend.toInt().toString(), Modifier.align(Alignment.BottomStart), fontSize = 8.sp, fontWeight = FontWeight.Medium, color = amountColor)
                                                if (hasBill) Text("!", Modifier.align(Alignment.TopEnd), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEE5253))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Stats
                    Row(Modifier.fillMaxWidth().padding(16.dp, 20.dp, 16.dp, 0.dp), Arrangement.spacedBy(8.dp)) {
                        SmallStatCard("No-spend\ndays", cNoSpendDays.toString(), Modifier.weight(1f))
                        SmallStatCard("Avg\nper day", fmt.format(cAvgSpend), Modifier.weight(1f))
                        SmallStatCard("Top\nday", if (cTopDay > 0) "Day $cTopDay" else "—", Modifier.weight(1f))
                    }
                }
            }
        }

        // Selected Day Bills
        if (viewModel.calendarDay != null && dayBills.isNotEmpty()) {
            item {
                Text("Bills Due", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEE5253), modifier = Modifier.padding(24.dp, 20.dp, 24.dp, 8.dp))
            }
            items(dayBills) { bill ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEE5253).copy(alpha = 0.1f))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CreditCard, null, tint = Color(0xFFEE5253))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(bill.bankName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(bill.cardDigits, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(fmt.format(bill.totalDue), fontWeight = FontWeight.Bold, color = Color(0xFFEE5253))
                    }
                }
            }
        }

        // Selected Day Transactions
        if (viewModel.calendarDay != null && dayTxns.isNotEmpty()) {
            item {
                val daySpend = dayTxns.filter { it.isExpense }.sumOf { t ->
                    val tSplits = splitsByTxn[t.id].orEmpty()
                    if (tSplits.isNotEmpty()) tSplits.filter { it.category != "Shared/Other" }.sumOf { it.amount } else t.amount
                } - dayTxns.filter { it.isRefund }.sumOf { it.amount }
                val netDaySpend = maxOf(0.0, daySpend)
                Row(
                    Modifier.fillMaxWidth().padding(24.dp, 20.dp, 24.dp, 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Transactions", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Spent: ${fmt.format(netDaySpend)}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(dayTxns, key = { it.id }) { t ->
                val tSplits = splitsByTxn[t.id].orEmpty()
                val displayAmount = if (tSplits.isNotEmpty()) {
                    tSplits.filter { it.category != "Shared/Other" }.sumOf { it.amount }
                } else {
                    t.amount
                }
                Row(
                    Modifier.fillMaxWidth().clickable { onTransactionClick(t) }.padding(16.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(36.dp).background(CatColor.bg(t.category), RoundedCornerShape(10.dp)), Alignment.Center) {
                        Box(Modifier.size(14.dp).background(CatColor.tone(t.category), CircleShape))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(t.merchant, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.category, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val isIncome = t.isIncome
                    Text(
                        if (isIncome) "+ " + fmt.format(displayAmount) else fmt.format(displayAmount), 
                        fontSize = 14.sp, 
                        fontWeight = FontWeight.Medium, 
                        color = if (t.isIgnored) MaterialTheme.colorScheme.onSurfaceVariant else if (isIncome) CatColor.tone("Income") else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (t.isIgnored) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                    )
                }
            }
        }
    }
}

@Composable
fun SmallStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, RoundedCornerShape(16.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp, lineHeight = 13.sp)
            Text(value, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
