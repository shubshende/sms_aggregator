package com.example.smsaggregator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smsaggregator.data.CreditCardBill
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BillsScreen(
    viewModel: TransactionViewModel,
    bills: List<CreditCardBill>,
    modifier: Modifier = Modifier
) {
    val fmt = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val today = System.currentTimeMillis()

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Text(
            "Upcoming Bills",
            modifier = Modifier.padding(24.dp, 24.dp, 24.dp, 8.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (bills.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ReceiptLong, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(16.dp))
                    Text("No bills detected yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(bills.sortedBy { it.dueDate }) { bill ->
                    BillItem(bill, fmt, dateFmt, today) {
                        viewModel.markBillAsPaid(bill.id)
                    }
                }
            }
        }
    }
}

@Composable
fun BillItem(
    bill: CreditCardBill,
    fmt: NumberFormat,
    dateFmt: SimpleDateFormat,
    today: Long,
    onMarkPaid: () -> Unit
) {
    val daysLeft = ((bill.dueDate - today) / (24 * 60 * 60 * 1000)).toInt()
    val statusColor = when {
        bill.isPaid -> Color(0xFF1DD1A1)
        daysLeft < 0 -> Color(0xFFEE5253)
        daysLeft <= 5 -> Color(0xFFFF9F43)
        else -> Color(0xFF2E86DE)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp)), Alignment.Center) {
                    Icon(Icons.Outlined.CreditCard, null, tint = statusColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(bill.bankName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(bill.cardDigits, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (bill.isPaid) {
                    Icon(Icons.Outlined.CheckCircle, "Paid", tint = Color(0xFF1DD1A1))
                } else {
                    Text(
                        if (daysLeft < 0) "Overdue" else "Due in $daysLeft days",
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Bottom) {
                Column {
                    Text("Total Due", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmt.format(bill.totalDue), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (bill.minDue > 0) {
                        Text("Min: ${fmt.format(bill.minDue)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Due Date", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(dateFmt.format(Date(bill.dueDate)), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (!bill.isPaid) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onMarkPaid,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = statusColor)
                ) {
                    Text("Mark as Paid")
                }
            }
        }
    }
}
