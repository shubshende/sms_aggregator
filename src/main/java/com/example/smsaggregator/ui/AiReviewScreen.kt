package com.example.smsaggregator.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AiReviewScreen(viewModel: TransactionViewModel, report: AiReport, onBack: () -> Unit) {
    val sortedItems = remember(report) { report.items.filter { it.isSorted } }
    val unrecognizedItems = remember(report) { report.items.filter { !it.isSorted } }
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var mappingItem by remember { mutableStateOf<AiReportItem?>(null) }

    if (mappingItem != null) {
        ManualMapDialog(
            item = mappingItem!!,
            onDismiss = { mappingItem = null },
            onSave = { name, category ->
                viewModel.mapAiReportItem(mappingItem!!, name, category)
                mappingItem = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(horizontal = 24.dp)) {
        Spacer(modifier = Modifier.height(48.dp))
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
            Text("← Back to Dashboard", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Self-Learning AI Center", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text("Review and correct the brain's logic", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Tab Switcher
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(4.dp)
        ) {
            val selColor = MaterialTheme.colorScheme.primary
            val unselColor = Color.Transparent
            
            Box(
                modifier = Modifier.weight(1f).background(if (selectedTab == 0) selColor else unselColor, RoundedCornerShape(12.dp))
                    .clickable { selectedTab = 0 }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Auto-Sorted (${sortedItems.size})", color = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier.weight(1f).background(if (selectedTab == 1) MaterialTheme.colorScheme.error else unselColor, RoundedCornerShape(12.dp))
                    .clickable { selectedTab = 1 }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Needs Help (${unrecognizedItems.size})", color = if (selectedTab == 1) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        val displayItems = if (selectedTab == 0) sortedItems else unrecognizedItems
        
        if (displayItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions in this category", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                items(displayItems) { item ->
                    AiReviewRow(item, onClick = { mappingItem = item })
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualMapDialog(item: AiReportItem, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var merchantName by remember { mutableStateOf(if (item.merchant == "Unknown") "" else item.merchant) }
    var selectedCategory by remember { mutableStateOf(item.category) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    val allCats = listOf(
        "Food & Dining", "Groceries", "Gold & Jewellery", "Fuel", "Shopping",
        "Travel", "Utilities", "Telecom", "Entertainment", "Health",
        "Fitness", "Education", "Insurance", "Investments", "EMI / Loan",
        "Rent", "Auto Debit", "UPI Transfer", "Wallet Topup", "ATM",
        "Government", "Home & Lifestyle", "Other"
    )

    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("Select Category") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(allCats) { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedCategory = category
                                showCategoryPicker = false
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(getCategoryEmoji(category), fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(category, color = if (category == selectedCategory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showCategoryPicker = false }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Map Transaction", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Help the AI learn by naming this vendor and setting its category.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(20.dp))
                
                Text("Merchant Name", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                TextField(
                    value = merchantName,
                    onValueChange = { merchantName = it },
                    placeholder = { Text("e.g. Zomato, Fuel Station") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))
                Text("Category", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .clickable { showCategoryPicker = true }.padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(getCategoryEmoji(selectedCategory), fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(selectedCategory, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("Edit", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(merchantName, selectedCategory) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Mapping")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AiReviewRow(item: AiReportItem, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(getCategoryEmoji(item.category), fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.merchant, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.category, fontSize = 12.sp, color = if (item.isSorted) Color(0xFF1DD1A1) else MaterialTheme.colorScheme.error)
                        if (!item.isSorted) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("⚠️ Tap to Map", fontSize = 10.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(12.dp)
            ) {
                Text(item.sms, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
