package com.message.bulksend.aiagent.tools.paymentverification

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

class OwnerPaymentApprovalActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val verificationId =
            intent.getStringExtra(OwnerPaymentReviewNotifier.EXTRA_VERIFICATION_ID).orEmpty()
        val manager = PaymentVerificationManager.getInstance(this)

        setContent {
            MaterialTheme {
                OwnerPaymentApprovalScreen(
                    manager = manager,
                    verificationId = verificationId,
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
private fun OwnerPaymentApprovalScreen(
    manager: PaymentVerificationManager,
    verificationId: String,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var verification by remember { mutableStateOf<PaymentVerification?>(null) }

    LaunchedEffect(verificationId) {
        verification =
            if (verificationId.isNotBlank()) {
                manager.getVerificationById(verificationId)
            } else {
                manager.getLatestPendingVerification()
            }
        loading = false
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
    ) {
        Text(
            text = "Owner Payment Confirmation",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Confirm payment only after checking all field label/value matches.",
            fontSize = 13.sp,
            color = Color(0xFF475569)
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            loading -> {
                Text("Loading...", color = Color(0xFF334155))
            }

            verification == null -> {
                Text("No pending verification found.", color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(14.dp))
                Button(onClick = onClose) { Text("Close") }
            }

            else -> {
                val item = verification ?: return@Column
                VerificationDetailCard(item)
                Spacer(modifier = Modifier.height(14.dp))
                FieldMatchCard(item)

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                manager.approvePayment(
                                    item.id,
                                    notes = "Approved by owner from review screen"
                                )
                                Toast.makeText(
                                    context,
                                    "Payment approved",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onClose()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.height(0.dp))
                        Text("Approve")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                manager.rejectPayment(
                                    item.id,
                                    notes = "Rejected by owner from review screen"
                                )
                                Toast.makeText(
                                    context,
                                    "Payment rejected",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onClose()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.height(0.dp))
                        Text("Reject")
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationDetailCard(verification: PaymentVerification) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF2563EB))
                Text(
                    text = " ${verification.customerPhone.ifBlank { "Unknown" }}",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text("Order ID: ${verification.orderId.ifBlank { "-" }}", color = Color(0xFF334155))
            Text(
                "Amount: ${if (verification.amount > 0) "Rs ${formatAmount(verification.amount)}" else "-"}",
                color = Color(0xFF334155)
            )
            Text("UPI: ${verification.upiId.ifBlank { "-" }}", color = Color(0xFF334155))
            Text(
                "Txn: ${verification.transactionId.ifBlank { "-" }}",
                color = Color(0xFF334155)
            )
            Text(
                "AI Recommendation: ${verification.recommendation.ifBlank { "-" }}",
                color = Color(0xFF475569)
            )
            Text("Current Status: ${verification.status}", color = Color(0xFF0F172A))
        }
    }
}

@Composable
private fun FieldMatchCard(verification: PaymentVerification) {
    val customExpected = parseJsonMap(verification.customFieldsExpected)
    val customExtracted = parseJsonMap(verification.customFieldsExtracted)

    val rows = mutableListOf<FieldMatchRow>()
    if (verification.expectedName.isNotBlank()) {
        rows +=
            FieldMatchRow(
                label = "Name",
                expected = verification.expectedName,
                extracted = verification.payeeName.ifBlank { verification.payerName },
                matched = verification.nameMatched
            )
    }
    if (verification.expectedUpiId.isNotBlank()) {
        rows +=
            FieldMatchRow(
                label = "UPI ID",
                expected = verification.expectedUpiId,
                extracted = verification.upiId,
                matched = verification.upiMatched
            )
    }
    if (verification.expectedAmount > 0) {
        rows +=
            FieldMatchRow(
                label = "Amount",
                expected = formatAmount(verification.expectedAmount),
                extracted = if (verification.amount > 0) formatAmount(verification.amount) else "",
                matched = verification.amountMatched
            )
    }

    customExpected.forEach { (label, expectedValue) ->
        rows +=
            FieldMatchRow(
                label = label,
                expected = expectedValue,
                extracted = customExtracted[label].orEmpty(),
                matched =
                    customExtracted[label]
                        ?.trim()
                        ?.equals(expectedValue.trim(), ignoreCase = true) == true
            )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Field Label/Value Match",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            if (rows.isEmpty()) {
                Text(
                    "No expected fields configured. Auto-confirm should stay OFF.",
                    color = Color(0xFFB45309),
                    fontSize = 12.sp
                )
            } else {
                rows.forEach { row ->
                    MatchRow(row)
                }
            }
        }
    }
}

@Composable
private fun MatchRow(row: FieldMatchRow) {
    val matchColor = if (row.matched) Color(0xFF15803D) else Color(0xFFB91C1C)
    Column {
        Text("${row.label}: ${if (row.matched) "MATCH" else "MISMATCH"}", color = matchColor)
        Text("Expected: ${row.expected.ifBlank { "-" }}", fontSize = 12.sp, color = Color(0xFF334155))
        Text("Extracted: ${row.extracted.ifBlank { "-" }}", fontSize = 12.sp, color = Color(0xFF334155))
    }
}

private data class FieldMatchRow(
    val label: String,
    val expected: String,
    val extracted: String,
    val matched: Boolean
)

private fun parseJsonMap(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return try {
        val json = JSONObject(raw)
        val map = mutableMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.optString(key, "").trim()
            if (key.isNotBlank() && value.isNotBlank()) {
                map[key] = value
            }
        }
        map
    } catch (_: Exception) {
        emptyMap()
    }
}

private fun formatAmount(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale.US, "%.2f", value)
}
