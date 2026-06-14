package com.example.smsaggregator.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.smsaggregator.data.Transaction
import com.example.smsaggregator.ui.theme.Dark_SurfCont

enum class M3Tab { HOME, BILLS, CALENDAR, BUDGETS, INSIGHTS }
enum class M3Overlay { NONE, DETAIL, SETTINGS, AI_REVIEW }

@Composable
fun M3AppContent(viewModel: TransactionViewModel, onSignIn: () -> Unit = {}) {
    var tab by remember { mutableStateOf(M3Tab.HOME) }
    var overlay by remember { mutableStateOf(M3Overlay.NONE) }
    var selectedTxn by remember { mutableStateOf<Transaction?>(null) }

    val transactions by viewModel.transactions.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val classifyResult by viewModel.classifyResult.collectAsState()
    val lastAiReport by viewModel.lastAiReport.collectAsState()
    val pendingCategory by viewModel.pendingCategory.collectAsState()
    val context = LocalContext.current

    // Tapping a category (e.g. a ring slice on Home) jumps to its Budgets transaction list.
    LaunchedEffect(pendingCategory) {
        if (pendingCategory != null) tab = M3Tab.BUDGETS
    }

    BackHandler(enabled = overlay != M3Overlay.NONE) {
        overlay = M3Overlay.NONE; selectedTxn = null
    }

    LaunchedEffect(classifyResult) {
        if (classifyResult != null) {
            when {
                classifyResult == "NO_KEY" -> Toast.makeText(context, "API Key required. Add in Settings.", Toast.LENGTH_LONG).show()
                classifyResult == "NONE" -> Toast.makeText(context, "All transactions already categorized.", Toast.LENGTH_SHORT).show()
                classifyResult?.startsWith("OK:") == true -> {
                    if (lastAiReport != null) overlay = M3Overlay.AI_REVIEW
                    else Toast.makeText(context, "AI sorted merchants!", Toast.LENGTH_SHORT).show()
                }
                classifyResult?.startsWith("API_ERROR_") == true || classifyResult?.startsWith("AI_ERROR") == true -> {
                    val msg = classifyResult?.substringAfter("_") ?: "Unknown error"
                    Toast.makeText(context, "AI Error: $msg", Toast.LENGTH_LONG).show()
                }
            }
            viewModel.clearClassifyResult()
        }
    }

    when (overlay) {
        M3Overlay.DETAIL -> {
            if (selectedTxn != null) {
                ExpenseDetailScreen(viewModel, selectedTxn!!, onBack = { overlay = M3Overlay.NONE; selectedTxn = null })
            }
            return
        }
        M3Overlay.SETTINGS -> {
            M3SettingsScreen(viewModel, onSignIn, onBack = { overlay = M3Overlay.NONE })
            return
        }
        M3Overlay.AI_REVIEW -> {
            if (lastAiReport != null) {
                AiReviewScreen(viewModel, lastAiReport!!, onBack = { overlay = M3Overlay.NONE })
            }
            return
        }
        M3Overlay.NONE -> {}
    }

    Scaffold(
        bottomBar = { M3BottomNav(tab) { tab = it } },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                    M3Tab.HOME -> M3HomeScreen(
                        viewModel = viewModel,
                        transactions = transactions,
                        budgets = budgets,
                        onTransactionClick = { selectedTxn = it; overlay = M3Overlay.DETAIL },
                        onAiClassify = { viewModel.classifyWithGemini() },
                        onSettingsClick = { overlay = M3Overlay.SETTINGS },
                        onCategoryClick = { viewModel.openCategory(it) }
                    )
                    M3Tab.BILLS -> BillsScreen(
                        viewModel = viewModel,
                        bills = bills
                    )
                    M3Tab.CALENDAR -> CalendarScreen(
                        viewModel = viewModel,
                        transactions = transactions,
                        onTransactionClick = { selectedTxn = it; overlay = M3Overlay.DETAIL }
                    )
                    M3Tab.BUDGETS -> BudgetsScreen(
                        viewModel = viewModel,
                        transactions = transactions,
                        budgets = budgets,
                        modifier = Modifier,
                        onTransactionClick = { selectedTxn = it; overlay = M3Overlay.DETAIL }
                    )
                    M3Tab.INSIGHTS -> InsightsScreen(
                        viewModel = viewModel,
                        transactions = transactions
                    )
                }
        }
    }
}

// ═══════════════════════════════════════════
// M3 Settings Screen
// ═══════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3SettingsScreen(viewModel: TransactionViewModel, onSignIn: () -> Unit, onBack: () -> Unit) {
    val authState by viewModel.authState.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    var apiKeyInput by remember { mutableStateOf(viewModel.getApiKey()) }

    // Backup / restore file pickers.
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportBackupToUri(it) } }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importBackupFromUri(it) } }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState())
    ) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(4.dp, 8.dp, 4.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }

        // ── Profile Section ──
        Card(Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 0.dp), RoundedCornerShape(24.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                if (authState == AuthState.SIGNED_IN) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (userProfile.photoUrl != null) {
                            AsyncImage(
                                model = userProfile.photoUrl,
                                contentDescription = "Profile",
                                modifier = Modifier.size(56.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), Alignment.Center) {
                                Text(userProfile.displayName.take(1).uppercase(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(userProfile.displayName, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text(userProfile.email, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (syncStatus != null) {
                        Text(syncStatus!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.syncWithCloud() },
                            Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Outlined.CloudSync, "Sync", Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sync", fontWeight = FontWeight.Medium)
                        }
                        OutlinedButton(
                            onClick = { viewModel.signOut() },
                            Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Text("Sign Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    Column {
                        Icon(Icons.Outlined.AccountCircle, "Account", Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text("Sign in to sync", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Backup data across devices with Google", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = onSignIn,
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Continue with Google", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // ── AI Configuration ──
        Card(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp), RoundedCornerShape(24.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, "AI", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("AI Configuration", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                Text("Gemini API key for intelligent categorization", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.saveApiKey(apiKeyInput) },
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Save Key", fontWeight = FontWeight.Medium) }
            }
        }

        // ── Preferences ──
        val isDark by viewModel.isDarkMode.collectAsState()
        val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
        Card(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp), RoundedCornerShape(24.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(4.dp, 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode, "Theme", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text("Dark Mode", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text(if (isDark) "On" else "Off", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isDark, onCheckedChange = { viewModel.setDarkMode(it) })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                Row(Modifier.fillMaxWidth().padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Fingerprint, "Biometric", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text("Biometric Lock", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text(if (isBiometricEnabled) "Enabled" else "Disabled", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isBiometricEnabled, onCheckedChange = { viewModel.setBiometricEnabled(it) })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(Icons.Outlined.Notifications, "Notifications", "Daily spending reminders")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(Icons.Outlined.Security, "Privacy", "Data & permissions")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(Icons.Outlined.FileDownload, "Export", "Download CSV report") {
                    viewModel.exportTransactionsToCsv(viewModel.transactions.value)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(Icons.Outlined.Backup, "Backup", "Save all data to a JSON file") {
                    backupLauncher.launch("expense_backup_${System.currentTimeMillis()}.json")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(Icons.Outlined.Restore, "Restore", "Import from a backup file") {
                    restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(Icons.Outlined.PictureAsPdf, "Monthly Report", "Share this month's summary as PDF") {
                    viewModel.exportMonthlyReportPdf()
                }
            }
        }

        // ── App Info ──
        Card(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp), RoundedCornerShape(24.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, "Info", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text("About", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(12.dp))
                InfoRow("Version", "8.0.0")
                InfoRow("Engine", "AI Classification v3")
                InfoRow("Cloud", if (authState == AuthState.SIGNED_IN) "Connected" else "Offline")
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, title, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, "Go", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ═══════════════════════════════════════════
// Bottom Navigation
// ═══════════════════════════════════════════
@Composable
fun M3BottomNav(current: M3Tab, onSelect: (M3Tab) -> Unit) {
    val items = listOf(
        Triple(M3Tab.HOME, "Home", Icons.Outlined.Home),
        Triple(M3Tab.BILLS, "Bills", Icons.Outlined.ReceiptLong),
        Triple(M3Tab.CALENDAR, "Calendar", Icons.Outlined.DateRange),
        Triple(M3Tab.BUDGETS, "Budgets", Icons.Outlined.AccountBalanceWallet),
        Triple(M3Tab.INSIGHTS, "Insights", Icons.Outlined.Insights)
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        items.forEach { (tab, label, icon) ->
            val sel = tab == current
            NavigationBarItem(
                selected = sel,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, label, tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                label = { Text(label, fontSize = 11.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    }
}
