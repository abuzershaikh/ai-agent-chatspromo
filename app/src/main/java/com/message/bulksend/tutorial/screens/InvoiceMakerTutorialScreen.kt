package com.message.bulksend.tutorial.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InvoiceMakerTutorialScreen() {
    val steps = listOf(
        TutorialStep("🏢 Setup Business Profile", "Add your business name, logo, address, GST number and bank details.", "1"),
        TutorialStep("📝 Create New Invoice", "Tap 'Create Invoice' to start making a new invoice for your customer.", "2"),
        TutorialStep("👤 Add Customer Details", "Enter customer name, phone, email and address.", "3"),
        TutorialStep("📦 Add Items/Services", "Add products or services with quantity, rate, tax and discount.", "4"),
        TutorialStep("💰 Tax & Discount", "Apply GST, CGST, SGST or custom tax. Add discounts per item or total.", "5"),
        TutorialStep("📋 Invoice Number", "Auto-generated invoice numbers or set your own custom format.", "6"),
        TutorialStep("📄 Preview Invoice", "Preview how your invoice will look before saving or sharing.", "7"),
        TutorialStep("📤 Export & Share", "Export as PDF or PNG. Share directly via WhatsApp, Email or other apps.", "8"),
        TutorialStep("📊 Invoice History", "View all past invoices, filter by date, customer or status.", "9"),
        TutorialStep("💳 Payment Status", "Mark invoices as Paid, Pending or Overdue for easy tracking.", "10")
    )
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Invoice Maker - Professional Invoices",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Create and share professional invoices in seconds",
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        items(steps.size) { index ->
            TutorialStepCard(steps[index])
        }
        
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
