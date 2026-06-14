package com.example.smsaggregator.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smsaggregator.data.Transaction
import com.example.smsaggregator.ui.theme.CatColor
import com.example.smsaggregator.ui.theme.Dark_SurfCont
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExpenseDetailScreen(
    viewModel: TransactionViewModel,
    transaction: Transaction,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fmt = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 } }
    var currentCat by remember { mutableStateOf(transaction.category) }
    var isIgnored by remember { mutableStateOf(transaction.isIgnored) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val allCats = listOf(
        "Food & Dining","Groceries","Transport","Travel","Bills","Utilities","Telecom",
        "Shopping","Entertainment","Health","Fitness","Education","Investments","Insurance",
        "EMI / Loan","Rent","Auto Debit","UPI Transfer","Wallet Topup","ATM","Government",
        "Gold & Jewellery","Home & Lifestyle","Fuel","Other"
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transaction") },
            text = { Text("Are you sure you want to delete this transaction? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction(transaction)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState())) {
        // ─── Top Bar ───
        Row(Modifier.fillMaxWidth().padding(4.dp, 8.dp, 4.dp, 0.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface) }
            IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Outlined.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        // ─── Hero Amount ───
        Column(Modifier.padding(24.dp, 16.dp, 24.dp, 4.dp)) {
            Text("AMOUNT", fontSize = 11.sp, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text("₹", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(
                    NumberFormat.getNumberInstance(Locale("en", "IN")).format(transaction.amount),
                    fontSize = 56.sp, color = if (isIgnored) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Light, letterSpacing = (-2).sp,
                    textDecoration = if (isIgnored) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                )
            }
            Text("${transaction.merchant} · $currentCat", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 12.dp))
            val dateStr = SimpleDateFormat("EEE, dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(transaction.date))
            Text("$dateStr · ${transaction.source}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }

        // ─── Category Picker ───
        Column(Modifier.padding(top = 24.dp)) {
            Text("CATEGORY", fontSize = 11.sp, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allCats.size) { i ->
                    val c = allCats[i]
                    val sel = c == currentCat
                    Box(
                        Modifier.height(36.dp)
                            .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                            .border(if (sel) 0.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .clickable {
                                currentCat = c
                                viewModel.overrideCategory(transaction, c)
                            }
                            .padding(horizontal = 14.dp),
                        Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(CatColor.tone(c), CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(c, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        // ─── Detail Rows ───
        Card(Modifier.fillMaxWidth().padding(16.dp, 24.dp, 16.dp, 0.dp), RoundedCornerShape(20.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column {
                M3DetailRow(Icons.Outlined.CalendarToday, "Date", SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(transaction.date)))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 14.dp))
                M3DetailRow(Icons.Outlined.CreditCard, "Paid from", transaction.source)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 14.dp))
                M3DetailRow(Icons.Outlined.SwapVert, "Type", transaction.type.name)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 14.dp))
                
                // Exclude toggle
                Row(Modifier.fillMaxWidth().padding(14.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.surface, CircleShape), Alignment.Center) {
                        Icon(Icons.Outlined.Block, "Exclude", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("Exclude from Totals", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("For friends or refunds", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                    Switch(
                        checked = isIgnored,
                        onCheckedChange = { 
                            isIgnored = it
                            viewModel.setTransactionIgnored(transaction, it) 
                        }
                    )
                }
            }
        }

        // ─── Split Transaction ───
        var showSplitDialog by remember { mutableStateOf(false) }
        val splits by viewModel.getSplitsForTransaction(transaction.id).collectAsState(emptyList())

        Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("SPLITS", fontSize = 11.sp, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { showSplitDialog = true }) {
                    Icon(Icons.Outlined.CallSplit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (splits.isEmpty()) "Split Bill" else "Edit Split")
                }
            }
            if (splits.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp), RoundedCornerShape(16.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        splits.forEachIndexed { index, split ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                                Text(split.category, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(fmt.format(split.amount), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            if (index < splits.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }

        if (showSplitDialog) {
            SplitDialog(
                totalAmount = transaction.amount,
                categories = allCats,
                initialSplits = splits,
                onDismiss = { showSplitDialog = false },
                onConfirm = { newSplits ->
                    viewModel.splitTransaction(transaction.id, newSplits.map { 
                        com.example.smsaggregator.data.TransactionSplit(
                            transactionId = transaction.id,
                            amount = it.amount,
                            category = it.category
                        )
                    })
                    showSplitDialog = false
                }
            )
        }

        // ─── Receipt Attachment ───
        val context = androidx.compose.ui.platform.LocalContext.current
        var receiptUri by remember { mutableStateOf<android.net.Uri?>(null) }
        
        val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { success ->
            if (success && receiptUri != null) {
                viewModel.updateTransactionReceipt(transaction, receiptUri.toString())
            }
        }

        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                val file = java.io.File(context.filesDir, "receipts/receipt_${transaction.id}.jpg")
                file.parentFile?.mkdirs()
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                receiptUri = uri
                cameraLauncher.launch(uri)
            } else {
                android.widget.Toast.makeText(context, "Camera permission required for receipts", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
            Text("RECEIPT", fontSize = 11.sp, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            
            if (transaction.receiptPath != null) {
                Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    coil.compose.AsyncImage(
                        model = transaction.receiptPath,
                        contentDescription = "Receipt",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                    IconButton(
                        onClick = { viewModel.updateTransactionReceipt(transaction, null) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Outlined.Close, "Remove", tint = Color.White)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        val permission = android.Manifest.permission.CAMERA
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            val file = java.io.File(context.filesDir, "receipts/receipt_${transaction.id}.jpg")
                            file.parentFile?.mkdirs()
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            receiptUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            permissionLauncher.launch(permission)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.AddAPhoto, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Attach Receipt Photo")
                }
            }
        }

        // ─── Message Context ───
        Column(Modifier.padding(24.dp, 24.dp, 24.dp, 0.dp)) {
            Text("MESSAGE CONTEXT", fontSize = 11.sp, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Card(Modifier.fillMaxWidth().padding(top = 8.dp), RoundedCornerShape(16.dp), CardDefaults.cardColors(containerColor = Dark_SurfCont)) {
                Text(transaction.rawSms, Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

data class SplitItemState(val category: String, val amount: Double)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitDialog(
    totalAmount: Double,
    categories: List<String>,
    initialSplits: List<com.example.smsaggregator.data.TransactionSplit>,
    onDismiss: () -> Unit,
    onConfirm: (List<SplitItemState>) -> Unit
) {
    val splits = remember { 
        mutableStateListOf<SplitItemState>().apply {
            if (initialSplits.isNotEmpty()) {
                addAll(initialSplits.map { SplitItemState(it.category, it.amount) })
            } else {
                add(SplitItemState("Other", totalAmount))
            }
        }
    }
    val currentTotal = splits.sumOf { it.amount }
    val fmt = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split Transaction") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Total: ${fmt.format(totalAmount)}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                
                // Ratio Presets
                Text("Quick Splits (Your part)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presets = listOf(
                        "1/2" to 0.5,
                        "1/3" to 1.0/3.0,
                        "1/4" to 0.25,
                        "2/3" to 2.0/3.0,
                        "Full" to 1.0
                    )
                    presets.forEach { (label, ratio) ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                val myPart = (totalAmount * ratio)
                                splits.clear()
                                splits.add(SplitItemState("My Expense", myPart))
                                if (myPart < totalAmount) {
                                    splits.add(SplitItemState("Shared/Other", totalAmount - myPart))
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    splits.forEachIndexed { index, split ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            var expanded by remember { mutableStateOf(false) }
                            Box(Modifier.weight(0.6f)) {
                                OutlinedCard(onClick = { expanded = true }) {
                                    Text(split.category, Modifier.padding(12.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    (listOf("My Expense", "Shared/Other") + categories).distinct().forEach { cat ->
                                        DropdownMenuItem(text = { Text(cat) }, onClick = { 
                                            splits[index] = split.copy(category = cat)
                                            expanded = false 
                                        })
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = if (split.amount == 0.0) "" else "%.2f".format(split.amount),
                                onValueChange = { 
                                    val newVal = it.toDoubleOrNull() ?: 0.0
                                    splits[index] = split.copy(amount = newVal)
                                },
                                modifier = Modifier.weight(0.4f),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                            )
                            IconButton(onClick = { if (splits.size > 1) splits.removeAt(index) }) {
                                Icon(Icons.Outlined.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                
                TextButton(onClick = { splits.add(SplitItemState("Other", 0.0)) }) {
                    Icon(Icons.Outlined.Add, null)
                    Text("Add Custom Split")
                }
                
                if (Math.abs(currentTotal - totalAmount) > 0.01) {
                    Text("Remaining: ${fmt.format(totalAmount - currentTotal)}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = Math.abs(currentTotal - totalAmount) < 0.01,
                onClick = { onConfirm(splits) }
            ) { Text("Save Splits") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun M3DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(14.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), Alignment.Center) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp).weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
    }
}
