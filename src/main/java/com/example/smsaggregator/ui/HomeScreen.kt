package com.example.smsaggregator.ui

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smsaggregator.data.Budget
import com.example.smsaggregator.data.Transaction
import com.example.smsaggregator.data.isExpense
import com.example.smsaggregator.data.isIncome
import com.example.smsaggregator.data.isRefund
import com.example.smsaggregator.ui.theme.CatColor
import kotlin.math.roundToInt
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class CatSlice(val name: String, val spent: Double, val budget: Double, val color: Color, val bg: Color, val icon: String, val count: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3HomeScreen(
    viewModel: TransactionViewModel,
    transactions: List<Transaction>,
    budgets: List<Budget>,
    onTransactionClick: (Transaction) -> Unit,
    onAiClassify: () -> Unit,
    onSettingsClick: () -> Unit,
    onCategoryClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cal = Calendar.getInstance()
    val curMonth = cal.get(Calendar.MONTH)
    val curYear = cal.get(Calendar.YEAR)
    val userProfile by viewModel.userProfile.collectAsState()
    val isClassifying by viewModel.isClassifying.collectAsState()
    val otherCount = remember(transactions) { transactions.count { it.category == "Other" } }

    var showManualEntry by remember { mutableStateOf(false) }
    var selectedSource by remember { mutableStateOf("All") }

    // Search state — the search/filter UI lives in TransactionSearchScreen.
    var searchActive by remember { mutableStateOf(false) }

    // Drawer state
    var drawerOpen by remember { mutableStateOf(false) }
    // Long-press "peek" preview of a category (set from the ring chart).
    var peekCategory by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current

    val allSplits by viewModel.allSplits.collectAsState()
    // Precompute once (O(n)) instead of re-scanning the whole split list per transaction.
    val splitsByTxn = remember(allSplits) { allSplits.groupBy { it.transactionId } }

    val monthRange = remember(curYear, curMonth) { com.example.smsaggregator.util.DateUtils.monthRange(curYear, curMonth) }
    val monthTxns = remember(transactions, selectedSource, monthRange) {
        transactions.filter {
            !it.isIgnored &&
                it.date >= monthRange.first && it.date < monthRange.second &&
                (selectedSource == "All" || it.source == selectedSource)
        }
    }

    val slices = remember(monthTxns, budgets, allSplits) {
        val catMap = mutableMapOf<String, Double>()
        val cntMap = mutableMapOf<String, Int>()

        // 1. Sum only money-out (DEBIT) rows as spending.
        monthTxns.filter { it.isExpense }.forEach { t ->
            val tSplits = splitsByTxn[t.id].orEmpty()
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

        // 2. Net refunds/cashback against the category they belong to — you didn't
        //    really spend that money. Clamp each category at zero.
        monthTxns.filter { it.isRefund }.forEach { t ->
            if (catMap.containsKey(t.category)) {
                catMap[t.category] = maxOf(0.0, (catMap[t.category] ?: 0.0) - t.amount)
            }
        }

        catMap.entries.filter { it.value > 0 }.sortedByDescending { it.value }.map { (cat, spent) ->
            CatSlice(cat, spent, budgets.find { it.category == cat }?.monthlyLimit ?: 0.0, CatColor.tone(cat), CatColor.bg(cat), CatColor.icon(cat), cntMap[cat] ?: 0)
        }
    }
    val totalSpent = slices.sumOf { it.spent }
    val monthIncome = remember(monthTxns) { monthTxns.filter { it.isIncome }.sumOf { it.amount } }
    val bills by viewModel.bills.collectAsState()
    val unpaidBills = remember(bills) { bills.filter { !it.isPaid }.sortedBy { it.dueDate } }

    val fmt = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 } }
    val dayOfWeek = remember { SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()).uppercase() }
    val dateStr = remember { SimpleDateFormat("dd MMMM", Locale.getDefault()).format(Date()).uppercase() }
    val greeting = remember {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when { h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; else -> "Good evening" }
    }
    val firstName = remember(userProfile) { userProfile.displayName.split(" ").firstOrNull() ?: "User" }

    val ringProgress = remember { Animatable(0f) }
    LaunchedEffect(slices) { ringProgress.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }

    val isScanning by viewModel.isScanning.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Handle back button for overlays
    androidx.activity.compose.BackHandler(enabled = searchActive || drawerOpen) {
        if (searchActive) {
            searchActive = false
        } else if (drawerOpen) {
            drawerOpen = false
        }
    }

    // ── Drawer overlay ──
    if (drawerOpen) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { drawerOpen = false })
            Card(
                Modifier.width(280.dp).fillMaxHeight(),
                RoundedCornerShape(0.dp, 24.dp, 24.dp, 0.dp),
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(24.dp, 48.dp, 24.dp, 24.dp)) {
                    Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), Alignment.Center) {
                        Text(firstName.take(2).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("$greeting, $firstName", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text(userProfile.email, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    DrawerItem(Icons.Outlined.Home, "Home") { drawerOpen = false }
                    DrawerItem(Icons.Outlined.Refresh, "Scan SMS Inbox") { 
                        drawerOpen = false 
                        android.widget.Toast.makeText(context, "Scanning for transactions...", android.widget.Toast.LENGTH_SHORT).show()
                        viewModel.refreshSms()
                    }
                    DrawerItem(Icons.Outlined.Settings, "Settings") { drawerOpen = false; onSettingsClick() }
                    DrawerItem(Icons.Outlined.FileDownload, "Export CSV") { 
                        drawerOpen = false 
                        viewModel.exportTransactionsToCsv(monthTxns)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Expense Tracker v8.0", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    // ── Search overlay ──
    if (searchActive) {
        TransactionSearchScreen(
            viewModel = viewModel,
            transactions = transactions,
            splitsByTxn = splitsByTxn,
            fmt = fmt,
            onTransactionClick = { onTransactionClick(it); searchActive = false },
            onClose = { searchActive = false }
        )
        return
    }

    // ── Category peek (long-press on a ring slice) ──
    if (peekCategory != null) {
        val cat = peekCategory!!
        val catTxns = remember(cat, monthTxns) {
            monthTxns.filter { it.category == cat && it.isExpense }.sortedByDescending { it.amount }
        }
        CategoryPeekDialog(
            category = cat,
            transactions = catTxns,
            splitsByTxn = splitsByTxn,
            fmt = fmt,
            onDismiss = { peekCategory = null },
            onViewAll = { peekCategory = null; onCategoryClick(cat) },
            onTransactionClick = { peekCategory = null; onTransactionClick(it) }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showManualEntry = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Outlined.Add, "Add")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Top Bar
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    IconButton(onClick = { drawerOpen = true }) {
                        Icon(Icons.Outlined.Menu, "Menu", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { 
                            android.widget.Toast.makeText(context, "Scanning for transactions...", android.widget.Toast.LENGTH_SHORT).show()
                            viewModel.refreshSms() 
                        }) {
                            if (isScanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Refresh, "Scan SMS", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Outlined.Search, "Search", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Box(
                            Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).clickable { onSettingsClick() },
                            Alignment.Center
                        ) {
                            Text(firstName.take(2).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            // Greeting
            item {
                Column(Modifier.padding(24.dp, 12.dp, 24.dp, 4.dp)) {
                    Text("$dayOfWeek, $dateStr", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.2.sp, fontWeight = FontWeight.Medium)
                    Text("$greeting,\n$firstName", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 34.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }

            // Ring Chart
            item {
                val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()
                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 24.dp), Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(280.dp), Alignment.Center) {
                        Canvas(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(slices, totalSpent) {
                                    fun categoryAt(tap: androidx.compose.ui.geometry.Offset): String? {
                                        if (totalSpent <= 0) return null
                                        val sw = 20.dp.toPx()
                                        val radius = kotlin.math.min(size.width, size.height) / 3.4f
                                        val cx = size.width / 2f
                                        val cy = size.height / 2f
                                        val dx = tap.x - cx
                                        val dy = tap.y - cy
                                        val dist = kotlin.math.hypot(dx, dy)
                                        val slack = 14.dp.toPx()
                                        if (dist < radius - sw / 2f - slack || dist > radius + sw / 2f + slack) return null
                                        var angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble()))
                                        if (angle < -90.0) angle += 360.0
                                        var cursor = -90.0
                                        for (s in slices.take(6)) {
                                            val sweepDeg = (s.spent / totalSpent) * 360.0
                                            if (angle >= cursor && angle < cursor + sweepDeg) return s.name
                                            cursor += sweepDeg
                                        }
                                        return null
                                    }
                                    detectTapGestures(
                                        onTap = { tap -> categoryAt(tap)?.let { onCategoryClick(it) } },
                                        onLongPress = { tap ->
                                            categoryAt(tap)?.let {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                peekCategory = it
                                            }
                                        }
                                    )
                                }
                        ) {
                            val sw = 20.dp.toPx()
                            val radius = size.minDimension / 3.4f
                            drawCircle(color = Color.LightGray.copy(alpha = 0.2f), radius = radius, style = Stroke(sw))
                            if (totalSpent > 0) {
                                val namePaint = android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    color = labelColor
                                    textSize = 10.sp.toPx()
                                }
                                var start = -90f
                                slices.take(6).forEach { s ->
                                    val frac = (s.spent / totalSpent).toFloat()
                                    val sweep = (frac * 360f * ringProgress.value)
                                    if (sweep > 2f) {
                                        drawArc(
                                            color = s.color, 
                                            startAngle = start, 
                                            sweepAngle = sweep - 2f, 
                                            useCenter = false, 
                                            topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                                            style = Stroke(sw, cap = StrokeCap.Butt)
                                        )

                                        // Leader line + label for slices that are big enough to read.
                                        if (frac > 0.04f && ringProgress.value > 0.99f) {
                                            val midRad = Math.toRadians((start + sweep / 2f).toDouble())
                                            val cosA = kotlin.math.cos(midRad).toFloat()
                                            val sinA = kotlin.math.sin(midRad).toFloat()
                                            val r1 = radius + sw / 2f
                                            val r2 = r1 + 12.dp.toPx()
                                            val elbow = androidx.compose.ui.geometry.Offset(center.x + cosA * r2, center.y + sinA * r2)
                                            val onRight = cosA >= 0f
                                            val tickLen = 10.dp.toPx()
                                            val tickEnd = androidx.compose.ui.geometry.Offset(
                                                elbow.x + if (onRight) tickLen else -tickLen,
                                                elbow.y
                                            )
                                            drawLine(s.color, androidx.compose.ui.geometry.Offset(center.x + cosA * r1, center.y + sinA * r1), elbow, strokeWidth = 1.5.dp.toPx())
                                            drawLine(s.color, elbow, tickEnd, strokeWidth = 1.5.dp.toPx())

                                            val pct = (frac * 100).roundToInt()
                                            val shortName = if (s.name.length > 11) s.name.take(10) + "…" else s.name
                                            val label = "$shortName  $pct%"
                                            namePaint.textAlign = if (onRight) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT
                                            val textX = tickEnd.x + if (onRight) 4.dp.toPx() else -4.dp.toPx()
                                            val textY = tickEnd.y + 4.dp.toPx()
                                            drawContext.canvas.nativeCanvas.drawText(label, textX, textY, namePaint)
                                        }
                                    }
                                    start += sweep
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SPENT", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)
                            Text(fmt.format(totalSpent), fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
                            if (monthIncome > 0) {
                                Text(
                                    "+ ${fmt.format(monthIncome)} received",
                                    fontSize = 11.sp,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // AI Button
            if (otherCount > 0) {
                item {
                    Card(Modifier.fillMaxWidth().padding(16.dp, 20.dp, 16.dp, 0.dp).clickable { if (!isClassifying) onAiClassify() }, RoundedCornerShape(20.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoAwesome, "AI", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(14.dp))
                            Text("AI Classify ($otherCount items)", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            if (isClassifying) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Upcoming Bills Section
            if (unpaidBills.isNotEmpty()) {
                item {
                    Column(Modifier.padding(top = 20.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            Arrangement.SpaceBetween,
                            Alignment.CenterVertically
                        ) {
                            Text("Upcoming Bills", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            // View All button could potentially change the tab if passed a callback
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Spacer(Modifier.width(8.dp))
                            unpaidBills.take(5).forEach { bill ->
                                BillCard(bill, fmt) { viewModel.markBillAsPaid(bill.id) }
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            }

            // Filter Chips
            item {
                val sources = remember(transactions) { listOf("All") + transactions.map { it.source }.distinct().sorted() }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).horizontalScroll(rememberScrollState()),
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

            // List
            item {
                Text("Recent", Modifier.padding(24.dp, 8.dp, 24.dp, 12.dp), fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
            val filteredTransactions = transactions.filter { if (selectedSource == "All") true else it.source == selectedSource }
            if (filteredTransactions.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp, 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.ReceiptLong, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (selectedSource == "All") "No transactions yet" else "No transactions for this source",
                            fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Scan your SMS inbox to pull in bank transactions, or add one manually with the + button.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                android.widget.Toast.makeText(context, "Scanning for transactions...", android.widget.Toast.LENGTH_SHORT).show()
                                viewModel.refreshSms()
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Scan SMS Inbox")
                        }
                    }
                }
            } else {
                items(filteredTransactions.take(20), key = { it.id }) { t ->
                    val tSplits = splitsByTxn[t.id].orEmpty()
                    val displayAmount = if (tSplits.isNotEmpty()) {
                        tSplits.filter { it.category != "Shared/Other" }.sumOf { it.amount }
                    } else {
                        t.amount
                    }
                    TransactionItem(t, displayAmount, fmt) { onTransactionClick(t) }
                }
            }
        }
    }

    if (showManualEntry) {
        ManualTransactionDialog(viewModel) { showManualEntry = false }
    }
}

@Composable
fun BillCard(bill: com.example.smsaggregator.data.CreditCardBill, fmt: NumberFormat, onMarkPaid: () -> Unit) {
    val today = System.currentTimeMillis()
    val daysLeft = ((bill.dueDate - today) / (24 * 60 * 60 * 1000)).toInt()
    val statusColor = when {
        daysLeft < 0 -> Color(0xFFEE5253)
        daysLeft <= 5 -> Color(0xFFFF9F43)
        else -> Color(0xFF2E86DE)
    }

    Card(
        Modifier.width(180.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), Alignment.Center) {
                    Icon(Icons.Outlined.CreditCard, null, tint = statusColor, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(bill.bankName, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            Text(fmt.format(bill.totalDue), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                if (daysLeft < 0) "Overdue" else "Due in $daysLeft days",
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onMarkPaid,
                modifier = Modifier.fillMaxWidth().height(32.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = statusColor)
            ) {
                Text("Mark Paid", fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun TransactionItem(t: Transaction, displayAmount: Double, fmt: NumberFormat, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(CatColor.bg(t.category), RoundedCornerShape(12.dp)), Alignment.Center) {
            Box(Modifier.size(16.dp).background(CatColor.tone(t.category), CircleShape))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.merchant, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val dateText = remember(t.date) { com.example.smsaggregator.util.DateUtils.dayMonth(t.date) }
            Text("${t.category} · $dateText", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val isCredit = t.type == com.example.smsaggregator.data.TransactionType.CREDIT
        Text(
            text = if (isCredit) "+ ${fmt.format(displayAmount)}" else fmt.format(displayAmount),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isCredit) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
        )
    }
}

data class SavedFilter(val name: String, val type: String, val amount: String, val cats: Set<String>)

private fun loadSavedFilters(context: android.content.Context): List<SavedFilter> {
    val raw = context.getSharedPreferences("sms_agg_prefs", android.content.Context.MODE_PRIVATE)
        .getString("saved_filters", "[]") ?: "[]"
    return try {
        val arr = org.json.JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val catsArr = o.optJSONArray("cats") ?: org.json.JSONArray()
            SavedFilter(
                name = o.optString("name", "Filter"),
                type = o.optString("type", "All"),
                amount = o.optString("amount", "Any"),
                cats = (0 until catsArr.length()).map { catsArr.getString(it) }.toSet()
            )
        }
    } catch (_: Exception) { emptyList() }
}

private fun persistSavedFilters(context: android.content.Context, filters: List<SavedFilter>) {
    val arr = org.json.JSONArray()
    filters.forEach { f ->
        arr.put(org.json.JSONObject().apply {
            put("name", f.name); put("type", f.type); put("amount", f.amount)
            put("cats", org.json.JSONArray(f.cats.toList()))
        })
    }
    context.getSharedPreferences("sms_agg_prefs", android.content.Context.MODE_PRIVATE)
        .edit().putString("saved_filters", arr.toString()).apply()
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionSearchScreen(
    viewModel: TransactionViewModel,
    transactions: List<Transaction>,
    splitsByTxn: Map<Long, List<com.example.smsaggregator.data.TransactionSplit>>,
    fmt: NumberFormat,
    onTransactionClick: (Transaction) -> Unit,
    onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val categories by viewModel.categories.collectAsState()

    var query by remember { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    LaunchedEffect(query) { kotlinx.coroutines.delay(220); debounced = query }

    var filterType by remember { mutableStateOf("All") }       // All / Debits / Credits
    var amountBand by remember { mutableStateOf("Any") }       // Any / <500 / 500-5k / >5k
    var selectedCats by remember { mutableStateOf<Set<String>>(emptySet()) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    var savedFilters by remember { mutableStateOf(loadSavedFilters(context)) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showCatMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val txnCats = remember(transactions) { transactions.map { it.category }.distinct().sorted() }

    val results = remember(transactions, debounced, filterType, amountBand, selectedCats) {
        val q = debounced.lowercase().trim()
        transactions.asSequence().filter { t ->
            val textOk = q.length < 2 ||
                t.merchant.lowercase().contains(q) ||
                t.category.lowercase().contains(q) ||
                t.source.lowercase().contains(q) ||
                t.amount.toLong().toString().contains(q)
            val typeOk = when (filterType) {
                "Debits" -> t.type == com.example.smsaggregator.data.TransactionType.DEBIT
                "Credits" -> t.type == com.example.smsaggregator.data.TransactionType.CREDIT
                else -> true
            }
            val catOk = selectedCats.isEmpty() || t.category in selectedCats
            val amtOk = when (amountBand) {
                "< ₹500" -> t.amount < 500
                "₹500–5k" -> t.amount in 500.0..5000.0
                "> ₹5k" -> t.amount > 5000
                else -> true
            }
            textOk && typeOk && catOk && amtOk
        }.sortedByDescending { it.date }.take(300).toList()
    }

    val selectedTxns = remember(selectedIds, transactions) { transactions.filter { it.id in selectedIds } }

    fun clearSelection() { selectionMode = false; selectedIds = emptySet() }
    fun toggle(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
        if (selectedIds.isEmpty()) selectionMode = false
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(8.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (selectionMode) clearSelection() else onClose() }) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            if (selectionMode) {
                Text("${selectedIds.size} selected", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = { selectedIds = results.map { it.id }.toSet() }) { Text("All") }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search transactions...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        if (!selectionMode) {
            // Filter chips: type + amount band.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Debits", "Credits").forEach { t ->
                    FilterChip(selected = filterType == t, onClick = { filterType = t }, label = { Text(t) })
                }
                Spacer(Modifier.width(4.dp))
                listOf("< ₹500", "₹500–5k", "> ₹5k").forEach { band ->
                    FilterChip(selected = amountBand == band, onClick = { amountBand = if (amountBand == band) "Any" else band }, label = { Text(band) })
                }
            }
            // Category chips.
            if (txnCats.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    txnCats.forEach { c ->
                        FilterChip(
                            selected = c in selectedCats,
                            onClick = { selectedCats = if (c in selectedCats) selectedCats - c else selectedCats + c },
                            label = { Text(c, maxLines = 1) }
                        )
                    }
                }
            }
            // Saved filters + save action.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp, 0.dp, 12.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { showSaveDialog = true },
                    label = { Text("Save filter") },
                    leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, null, Modifier.size(18.dp)) }
                )
                savedFilters.forEach { f ->
                    AssistChip(
                        onClick = { filterType = f.type; amountBand = f.amount; selectedCats = f.cats },
                        label = { Text(f.name, maxLines = 1) },
                        trailingIcon = {
                            Icon(Icons.Outlined.Close, "Delete", Modifier.size(16.dp).clickable {
                                savedFilters = savedFilters.filter { it.name != f.name }
                                persistSavedFilters(context, savedFilters)
                            })
                        }
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (results.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.SearchOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No matching transactions", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                items(results, key = { it.id }) { t ->
                    val tSplits = splitsByTxn[t.id].orEmpty()
                    val displayAmount = if (tSplits.isNotEmpty()) {
                        tSplits.filter { it.category != "Shared/Other" }.sumOf { it.amount }
                    } else t.amount
                    val selected = t.id in selectedIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                            .combinedClickable(
                                onClick = { if (selectionMode) toggle(t.id) else onTransactionClick(t) },
                                onLongClick = { selectionMode = true; toggle(t.id) }
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectionMode) {
                            Checkbox(checked = selected, onCheckedChange = { toggle(t.id) })
                        }
                        Box(Modifier.weight(1f)) {
                            TransactionItem(t, displayAmount, fmt) {
                                if (selectionMode) toggle(t.id) else onTransactionClick(t)
                            }
                        }
                    }
                }
            }
        }

        // Bulk action bar.
        if (selectionMode && selectedIds.isNotEmpty()) {
            Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { showCatMenu = true }) {
                            Icon(Icons.Outlined.Category, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Categorize")
                        }
                        TextButton(onClick = { viewModel.bulkSetIgnored(selectedTxns, true); clearSelection() }) {
                            Icon(Icons.Outlined.VisibilityOff, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Ignore")
                        }
                        TextButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp)); Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    DropdownMenu(expanded = showCatMenu, onDismissRequest = { showCatMenu = false }) {
                        val catNames = (categories.map { it.name } + txnCats).distinct()
                        catNames.forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = {
                                viewModel.bulkSetCategory(selectedTxns, name); showCatMenu = false; clearSelection()
                            })
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save filter") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim().ifBlank { "Filter ${savedFilters.size + 1}" }
                    savedFilters = savedFilters.filter { it.name != n } + SavedFilter(n, filterType, amountBand, selectedCats)
                    persistSavedFilters(context, savedFilters)
                    showSaveDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${selectedIds.size} transactions?") },
            text = { Text("This permanently removes the selected transactions.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.bulkDelete(selectedTxns); showDeleteConfirm = false; clearSelection()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CategoryPeekDialog(
    category: String,
    transactions: List<Transaction>,
    splitsByTxn: Map<Long, List<com.example.smsaggregator.data.TransactionSplit>>,
    fmt: NumberFormat,
    onDismiss: () -> Unit,
    onViewAll: () -> Unit,
    onTransactionClick: (Transaction) -> Unit
) {
    // Spring-based grow-in for a smooth "peek/pop" feel.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.82f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "peekScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(160),
        label = "peekAlpha"
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            // Scrim — tap to dismiss.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f * alpha))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )

            val total = transactions.sumOf { it.amount }
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                    .pointerInput(Unit) { detectTapGestures { /* consume taps on the card */ } },
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(Modifier.padding(20.dp, 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(CatColor.bg(category), RoundedCornerShape(12.dp)), Alignment.Center) {
                            Text(CatColor.icon(category), fontSize = 18.sp)
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(category, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${transactions.size} transaction${if (transactions.size == 1) "" else "s"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(fmt.format(total), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (transactions.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), Alignment.Center) {
                            Text("No transactions this month", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(Modifier.heightIn(max = 340.dp)) {
                            items(transactions.take(25), key = { it.id }) { t ->
                                val tSplits = splitsByTxn[t.id].orEmpty()
                                val displayAmount = if (tSplits.isNotEmpty()) {
                                    tSplits.filter { it.category != "Shared/Other" }.sumOf { it.amount }
                                } else {
                                    t.amount
                                }
                                TransactionItem(t, displayAmount, fmt) { onTransactionClick(t) }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onViewAll, modifier = Modifier.align(Alignment.End)) {
                        Text("View all")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTransactionDialog(viewModel: TransactionViewModel, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Other") }
    var selectedSource by remember { mutableStateOf("Cash") }
    val categories by viewModel.categories.collectAsState()

    var selectedTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedTimestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val cal = Calendar.getInstance().apply {
                            val timePart = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                            timeInMillis = it
                            set(Calendar.HOUR_OF_DAY, timePart.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, timePart.get(Calendar.MINUTE))
                        }
                        selectedTimestamp = cal.timeInMillis
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val finalCal = Calendar.getInstance().apply {
                        timeInMillis = selectedTimestamp
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                    }
                    selectedTimestamp = finalCal.timeInMillis
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = timePickerState) }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("Merchant") }, modifier = Modifier.fillMaxWidth())
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedCard(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CalendarToday, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(dateFmt.format(Date(selectedTimestamp)), fontSize = 12.sp)
                        }
                    }
                    OutlinedCard(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(timeFmt.format(Date(selectedTimestamp)), fontSize = 12.sp)
                        }
                    }
                }

                Text("Category", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScrollableTabRow(
                        selectedTabIndex = categories.indexOfFirst { it.name == selectedCategory }.coerceAtLeast(0),
                        edgePadding = 0.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        categories.forEach { cat ->
                            Tab(selected = selectedCategory == cat.name, onClick = { selectedCategory = cat.name }, text = { Text(cat.name) })
                        }
                    }
                    IconButton(onClick = { /* show new category dialog */ }) {
                        Icon(Icons.Outlined.AddCircleOutline, null)
                    }
                }
                OutlinedTextField(value = selectedSource, onValueChange = { selectedSource = it }, label = { Text("Source") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (amt > 0 && merchant.isNotBlank()) {
                    viewModel.insertManualTransaction(amt, merchant, selectedCategory, selectedTimestamp, com.example.smsaggregator.data.TransactionType.DEBIT, selectedSource)
                    onDismiss()
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}
