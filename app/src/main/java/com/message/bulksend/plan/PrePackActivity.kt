package com.message.bulksend.plan

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.message.bulksend.auth.UserManager
import com.message.bulksend.autorespond.aireply.ChatsPromoAISubscriptionManager
import com.message.bulksend.referral.ReferralManager
import com.message.bulksend.ui.theme.BulksendTestTheme
import com.message.bulksend.ui.theme.PoppinsFamily
import com.message.bulksend.userdetails.UserDetailsPreferences
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class PrepackActivity : ComponentActivity(), PaymentResultListener {

        companion object {
                private const val TAG = "PrePackActivity"
                const val CREATE_ORDER_URL =
                        "https://us-central1-mailtracker-demo.cloudfunctions.net/createAIOrder"

                val httpClient =
                        OkHttpClient.Builder()
                                .connectTimeout(30, TimeUnit.SECONDS)
                                .readTimeout(30, TimeUnit.SECONDS)
                                .writeTimeout(30, TimeUnit.SECONDS)
                                .build()
        }

        private lateinit var billingManager: BillingManager
        private lateinit var aiBillingManager: AIBillingManager

        private val auth = FirebaseAuth.getInstance()
        private val userManager by lazy { UserManager(this) }
        private val userDetailsPreferences by lazy { UserDetailsPreferences(this) }
        private val referralManager by lazy { ReferralManager(this) }
        private val firestore = FirebaseFirestore.getInstance()

        private var productPrices by mutableStateOf<Map<String, String>>(emptyMap())
        private var showOnlyRazorpay by mutableStateOf(false)
        private var isPlanSyncing by mutableStateOf(false)

        private var currentOrderId: String? = null
        private var selectedPlanType: String = ""
        private var cachedRazorpayContact: String? = null

        private val getActivityLauncher =
                registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
                        ->
                        if (result.resultCode == RESULT_OK) {
                                refreshSubscriptionData { success ->
                                        if (success) {
                                                Handler(Looper.getMainLooper())
                                                        .postDelayed({ finish() }, 900)
                                        }
                                }
                        }
                }

        override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                enableEdgeToEdge()
                Checkout.preload(applicationContext)

                billingManager =
                        BillingManager(
                                context = this,
                                onPurchaseSuccess = {
                                        runOnUiThread {
                                                Toast.makeText(
                                                                this,
                                                                "Purchase successful. Premium unlocked.",
                                                                Toast.LENGTH_LONG
                                                        )
                                                        .show()
                                                Handler(Looper.getMainLooper())
                                                        .postDelayed({ finish() }, 1400)
                                        }
                                },
                                onPurchaseFailure = { error ->
                                        runOnUiThread {
                                                Toast.makeText(this, error, Toast.LENGTH_LONG)
                                                        .show()
                                        }
                                }
                        )
                billingManager.initialize()
                billingManager.setOnProductsLoadedListener { prices -> mergePlanPrices(prices) }

                aiBillingManager =
                        AIBillingManager(
                                context = this,
                                onPurchaseSuccess = {
                                        lifecycleScope.launch {
                                                ChatsPromoAISubscriptionManager(
                                                                this@PrepackActivity
                                                        )
                                                        .syncAfterPurchase()
                                        }
                                        runOnUiThread {
                                                Toast.makeText(
                                                                this,
                                                                "AI Agent plan activated.",
                                                                Toast.LENGTH_LONG
                                                        )
                                                        .show()
                                        }
                                },
                                onPurchaseFailure = { error ->
                                        runOnUiThread {
                                                Toast.makeText(this, error, Toast.LENGTH_LONG)
                                                        .show()
                                        }
                                }
                        )
                aiBillingManager.initialize()
                aiBillingManager.setOnProductsLoadedListener { prices -> mergePlanPrices(prices) }

                loadPaymentVisibilityConfig()
                loadRazorpayContactFromProfile()
                // Ensure local subscription prefs are fresh even when activity is opened from dashboard.
                refreshPlanCatalog(syncSubscription = true, showSyncToast = false)

                setContent {
                        BulksendTestTheme {
                                PriceTableScreen(
                                        activity = this,
                                        productPrices = productPrices,
                                        isSyncing = isPlanSyncing,
                                        onSyncPlans = { refreshPlanCatalog() },
                                        showOnlyRazorpay = showOnlyRazorpay,
                                        onBackPressed = { finish() }
                                )
                        }
                }
        }

        private fun mergePlanPrices(prices: Map<String, String>) {
                if (prices.isEmpty()) return
                val merged = productPrices.toMutableMap()
                merged.putAll(prices)
                productPrices = merged
        }

        private fun refreshPlanCatalog(
                syncSubscription: Boolean = true,
                showSyncToast: Boolean = true
        ) {
                if (isPlanSyncing) return

                isPlanSyncing = true
                var remainingCallbacks = if (syncSubscription) 3 else 2

                val markDone = {
                        remainingCallbacks -= 1
                        if (remainingCallbacks <= 0) {
                                isPlanSyncing = false
                        }
                }

                billingManager.setOnProductsLoadedListener { prices ->
                        mergePlanPrices(prices)
                        markDone()
                }
                aiBillingManager.setOnProductsLoadedListener { prices ->
                        mergePlanPrices(prices)
                        markDone()
                }

                if (syncSubscription) {
                        refreshSubscriptionData(showToast = false) { success ->
                                Log.d(TAG, "Subscription fetch during refresh success=$success")
                                markDone()
                        }
                }

                if (showSyncToast) {
                        runOnUiThread {
                                Toast.makeText(
                                                this,
                                                "Syncing plans and subscription...",
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                        }
                }

                billingManager.refreshProducts()
                aiBillingManager.refreshProducts()
        }

        private fun loadPaymentVisibilityConfig() {
                lifecycleScope.launch {
                        val userId = auth.currentUser?.uid

                        if (userId.isNullOrBlank()) {
                                showOnlyRazorpay = false
                                return@launch
                        }

                        val show =
                                runCatching {
                                                firestore
                                                        .collection("userDetails")
                                                        .document(userId)
                                                        .get()
                                                        .await()
                                                        .getLong("show")
                                                        ?.toInt()
                                                        ?: 0
                                        }
                                        .onFailure { error ->
                                                Log.e(
                                                        TAG,
                                                        "Failed to load payment visibility; defaulting to both options",
                                                        error
                                                )
                                        }
                                        .getOrDefault(0)

                        showOnlyRazorpay = show == 1

                        Log.d(
                                TAG,
                                "Payment visibility loaded from userDetails.show=$show, showOnlyRazorpay=$showOnlyRazorpay"
                        )
                }
        }

        private fun loadRazorpayContactFromProfile() {
                lifecycleScope.launch {
                        val contact = getRazorpayContactFromProfile()
                        cachedRazorpayContact = contact

                        Log.d(
                                "PrePackActivity",
                                "Loaded Razorpay contact from profile: ${contact ?: "not found"}"
                        )
                }
        }

        private fun openGetActivity(plan: String) {
                val intent =
                        Intent(this, GetActivity::class.java).apply {
                                putExtra("SELECTED_PLAN", plan)
                        }
                getActivityLauncher.launch(intent)
        }

        fun launchPremiumCheckout(plan: String) {
                openGetActivity(plan)
        }

        fun launchPlayStore(plan: String) {
                val productId =
                        when (plan) {
                                "monthly" -> BillingManager.PRODUCT_MONTHLY
                                "yearly" -> BillingManager.PRODUCT_YEARLY
                                else -> BillingManager.PRODUCT_LIFETIME
                        }
                billingManager.launchPurchaseFlow(this, productId)
        }

        fun refreshSubscriptionData(
                showToast: Boolean = true,
                onComplete: (Boolean) -> Unit
        ) {
                lifecycleScope.launch {
                        try {
                                val userEmail = auth.currentUser?.email
                                if (userEmail.isNullOrBlank()) {
                                        onComplete(false)
                                        return@launch
                                }

                                val userData = userManager.getUserData(userEmail)
                                if (userData == null) {
                                        onComplete(false)
                                        return@launch
                                }

                                val sharedPref =
                                        getSharedPreferences("subscription_prefs", MODE_PRIVATE)
                                with(sharedPref.edit()) {
                                        putString("subscription_type", userData.subscriptionType)
                                        putInt("contacts_limit", userData.contactsLimit)
                                        putInt("current_contacts", userData.currentContactsCount)
                                        putInt("groups_limit", userData.groupsLimit)
                                        putInt("current_groups", userData.currentGroupsCount)
                                        putString("user_email", userData.email)

                                        if (userData.subscriptionType == "premium") {
                                                userData.subscriptionEndDate?.let { endDate ->
                                                        putLong(
                                                                "subscription_end_time",
                                                                endDate.seconds * 1000
                                                        )
                                                }
                                        } else {
                                                remove("subscription_end_time")
                                        }

                                        apply()
                                }

                                if (showToast) {
                                        runOnUiThread {
                                                Toast.makeText(
                                                                this@PrepackActivity,
                                                                "Subscription refreshed.",
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                        }
                                }
                                onComplete(true)
                        } catch (_: Exception) {
                                onComplete(false)
                        }
                }
        }

        fun launchAIAgentPlayStore(productId: String = AIBillingManager.PRODUCT_AIAGENT_499) {
                selectedPlanType = productId
                aiBillingManager.launchPurchaseFlow(this, productId)
        }

        fun launchAIAgentRazorpay(planType: String = "aiagent499") {
                selectedPlanType = planType
                lifecycleScope.launch {
                        try {
                                val contact = getRazorpayContactFromProfile()
                                cachedRazorpayContact = contact
                                val order = createAIAgentOrder(planType)
                                currentOrderId = order.orderId
                                openCheckout(order, contact)
                        } catch (e: Exception) {
                                Toast.makeText(
                                                this@PrepackActivity,
                                                e.message ?: "Unable to create order.",
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                        }
                }
        }

        private fun openCheckout(order: AIAgentPlanOrder, contactNumber: String? = null) {
                val checkout = Checkout()
                checkout.setKeyID(order.keyId)

                try {
                        val options =
                                JSONObject().apply {
                                        put("name", "AI Agent Plan")
                                        put("description", order.planName)
                                        put("order_id", order.orderId)
                                        put("currency", order.currency)
                                        put("amount", order.amount)

                                        val prefill = JSONObject()
                                        val readonly = JSONObject()
                                        val email = auth.currentUser?.email
                                        if (!email.isNullOrBlank()) {
                                                prefill.put("email", email)
                                                readonly.put("email", true)
                                        }

                                        val finalContact =
                                                sanitizePhoneForRazorpay(
                                                        contactNumber ?: cachedRazorpayContact
                                                )
                                        if (!finalContact.isNullOrBlank()) {
                                                prefill.put("contact", finalContact)
                                                readonly.put("contact", true)
                                        }
                                        prefill.put("contact", finalContact ?: "")
                                        if (readonly.length() > 0) {
                                                put("readonly", readonly)
                                        }
                                        put("prefill", prefill)
                                        put("theme", JSONObject().apply { put("color", "#38BDF8") })
                                }
                        checkout.open(this, options)
                } catch (e: Exception) {
                        Toast.makeText(this, e.message ?: "Checkout failed.", Toast.LENGTH_LONG)
                                .show()
                }
        }

        private fun sanitizePhoneForRazorpay(phone: String?): String? {
                val digitsOnly = phone.orEmpty().filter { it.isDigit() }
                return if (digitsOnly.length >= 10) digitsOnly else null
        }

        private suspend fun getRazorpayContactFromProfile(): String? =
                withContext(Dispatchers.IO) {
                        val localPhone =
                                sanitizePhoneForRazorpay(userDetailsPreferences.getPhoneNumber())
                        if (!localPhone.isNullOrBlank()) {
                                return@withContext localPhone
                        }

                        val userId = auth.currentUser?.uid ?: return@withContext null
                        val document =
                                firestore.collection("userDetails").document(userId).get().await()

                        val rawPhone =
                                document.getString("phoneNumber")
                                        ?: document.getString("phone")
                                                ?: document.getString("mobile")
                                                ?: document.getString("contactNumber")

                        sanitizePhoneForRazorpay(rawPhone)
                }

        override fun onPaymentSuccess(razorpayPaymentId: String?) {
                val paymentId = razorpayPaymentId ?: return
                val orderId = currentOrderId ?: return

                lifecycleScope.launch {
                        val email = auth.currentUser?.email
                        if (email.isNullOrBlank()) {
                                return@launch
                        }

                        val success =
                                updateAgentSubscription(email, selectedPlanType, paymentId, orderId)

                        if (success) {
                                processReferralRewardForPurchase(selectedPlanType)
                                ChatsPromoAISubscriptionManager(this@PrepackActivity)
                                        .syncAfterPurchase()
                                Toast.makeText(
                                                this@PrepackActivity,
                                                "AI Agent plan activated.",
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                        }
                }
        }

        override fun onPaymentError(code: Int, response: String?) {
                Toast.makeText(this, response ?: "Payment failed.", Toast.LENGTH_LONG).show()
        }

        private suspend fun updateAgentSubscription(
                email: String,
                planType: String,
                paymentId: String,
                orderId: String
        ): Boolean =
                withContext(Dispatchers.IO) {
                        try {
                                val now = Timestamp.now()
                                val days = 30L
                                val endTime = Timestamp(now.seconds + (days * 24 * 60 * 60), 0)

                                firestore
                                        .collection("chatspromo_ai_subscriptions")
                                        .document(email.replace(".", "_"))
                                        .set(
                                                mapOf(
                                                        "email" to email,
                                                        "subscriptionType" to "premium",
                                                        "planType" to planType,
                                                        "subscriptionStartDate" to now,
                                                        "subscriptionEndDate" to endTime,
                                                        "lastPaymentId" to paymentId,
                                                        "lastOrderId" to orderId,
                                                        "lastPaymentDate" to now,
                                                        "paymentMethod" to "razorpay",
                                                        "isActive" to true
                                                )
                                        )
                                        .await()
                                true
                        } catch (_: Exception) {
                                false
                        }
                }

        private fun processReferralRewardForPurchase(planType: String) {
                val purchaseAmount =
                        when (planType.lowercase()) {
                                "aiagent499" -> 499
                                "ai_monthly" -> 199
                                "ai_yearly" -> 899
                                else -> 0
                        }

                if (purchaseAmount <= 0) {
                        Log.d(TAG, "AI plan amount not configured for referral reward: $planType")
                        return
                }

                lifecycleScope.launch {
                        try {
                                val result =
                                        referralManager.processReferralReward(
                                                planType = planType,
                                                purchaseAmount = purchaseAmount
                                        )
                                if (result.success) {
                                        Log.d(TAG, "AI referral reward processed: Rs ${result.commission}")
                                } else {
                                        Log.d(TAG, "AI referral reward not applicable: ${result.message}")
                                }
                        } catch (e: Exception) {
                                Log.e(TAG, "Error processing AI referral reward", e)
                        }
                }
        }

        override fun onDestroy() {
                super.onDestroy()
                billingManager.endConnection()
                aiBillingManager.endConnection()
        }
}

private data class AIAgentPlanOrder(
        val success: Boolean,
        val orderId: String,
        val amount: Int,
        val currency: String,
        val keyId: String,
        val planName: String
)

private fun formatPlanDisplayPrice(rawPrice: String): String {
        return rawPrice.replace(Regex("""(?<=\d)\.00(?=\D|$)"""), "")
}

private suspend fun createAIAgentOrder(planType: String): AIAgentPlanOrder =
        withContext(Dispatchers.IO) {
                val requestBody = JSONObject().apply { put("planType", planType) }

                val request =
                        Request.Builder()
                                .url(PrepackActivity.CREATE_ORDER_URL)
                                .post(
                                        requestBody
                                                .toString()
                                                .toRequestBody(
                                                        "application/json; charset=utf-8".toMediaType()
                                                )
                                )
                                .addHeader("Content-Type", "application/json")
                                .build()

                PrepackActivity.httpClient.newCall(request).execute().use { response ->
                        val payload = response.body?.string() ?: throw Exception("Empty response.")
                        if (!response.isSuccessful) {
                                throw Exception("HTTP ${response.code}")
                        }

                        val json = JSONObject(payload)
                        AIAgentPlanOrder(
                                success = json.getBoolean("success"),
                                orderId = json.getString("orderId"),
                                amount = json.getInt("amount"),
                                currency = json.getString("currency"),
                                keyId = json.getString("keyId"),
                                planName = json.getString("planName")
                        )
                }
        }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceTableScreen(
        activity: PrepackActivity,
        productPrices: Map<String, String>,
        isSyncing: Boolean,
        onSyncPlans: () -> Unit,
        showOnlyRazorpay: Boolean,
        onBackPressed: () -> Unit
) {
        var expandedCardId by remember { mutableStateOf("monthly") }
        var showPaymentDialog by remember { mutableStateOf(false) }
        var selectedCardId by remember { mutableStateOf("") }
        var infoDialogPlan by remember { mutableStateOf<PriceCardData?>(null) }

        val basePlanInfo =
                PlanInfo(
                        features =
                                listOf(
                                        "Remove branding and link from bulksender and autoreply",
                                        "Unlimited bulksend",
                                        "Unlimited autoreply to unlimited number",
                                        "Unlimited CRM"
                                )
                )

        val monthlyPrice =
                formatPlanDisplayPrice(productPrices[BillingManager.PRODUCT_MONTHLY] ?: "299₹")
        val yearlyPrice =
                formatPlanDisplayPrice(productPrices[BillingManager.PRODUCT_YEARLY] ?: "1499₹")
        val lifetimePrice =
                formatPlanDisplayPrice(productPrices[BillingManager.PRODUCT_LIFETIME] ?: "2999₹")
        val aiAgentPrice =
                formatPlanDisplayPrice(
                        productPrices[AIBillingManager.PRODUCT_AIAGENT_499] ?: "499₹"
                )

        val plans =
                listOf(
                        PriceCardData(
                                id = "monthly",
                                title = "Monthly",
                                price = monthlyPrice,
                                period = "per month",
                                description =
                                        "Unlimited bulk sends, campaign reports, high capacity, 30-day access.",
                                gradientColors =
                                        listOf(
                                                Color(0xFF6366F1),
                                                Color(0xFF4F46E5),
                                                Color(0xFF4338CA)
                                        ),
                                planInfo = basePlanInfo
                        ),
                        PriceCardData(
                                id = "yearly",
                                title = "Yearly",
                                price = yearlyPrice,
                                period = "per year",
                                description =
                                        "One-year premium access, unlimited tools, fewer renewal interruptions.",
                                gradientColors =
                                        listOf(
                                                Color(0xFF6366F1),
                                                Color(0xFF4F46E5),
                                                Color(0xFF4338CA)
                                        ),
                                planInfo = basePlanInfo
                        ),
                        PriceCardData(
                                id = "lifetime",
                                title = "Lifetime",
                                price = lifetimePrice,
                                period = "lifetime",
                                description =
                                        "One-time payment, permanent premium access, highest long-term value.",
                                gradientColors =
                                        listOf(
                                                Color(0xFF6366F1),
                                                Color(0xFF4F46E5),
                                                Color(0xFF4338CA)
                                        ),
                                planInfo = basePlanInfo
                        ),
                        PriceCardData(
                                id = "ai_agent",
                                title = "WhatsApp AI Agent",
                                price = aiAgentPrice,
                                period = "per month",
                                description =
                                        "AI Agent dashboard, 24x7 automated workflows, custom task execution.",
                                gradientColors =
                                        listOf(
                                                Color(0xFF059669),
                                                Color(0xFF047857),
                                                Color(0xFF065F46)
                                        ),
                                planInfo =
                                        PlanInfo(
                                                features =
                                                        listOf(
                                                                "Bundled with Bulksender, Auto Reply, and CRM in one WhatsApp AI Agent plan.",
                                                                "Autonomous AI Agent – Automatically replies to customers on WhatsApp.",
                                                                "Lead Qualification – AI chats with users and qualifies potential leads.",
                                                                "Payment Link Generation – Send Razorpay payment links directly in chat.",
                                                                "Product Catalogue Sharing – Instantly send product catalogues to customers.",
                                                                "Media Support – Send images, PDFs, and documents automatically.",
                                                                "Google Sheets Integration – Read and write customer data in Google Sheets or built-in tables.",
                                                                "Order Processing – Customers can place orders directly through WhatsApp.",
                                                                "Shipping & Courier Integration – Automatically create shipping requests.",
                                                                "Customer Data Forms – Send forms to collect address and other details.",
                                                                "Custom AI Templates – Automate business workflows with custom templates.",
                                                                "Voice Message Support – Understand and reply to voice messages.",
                                                                "24/7 Automation – AI handles conversations anytime without human support."
                                                        )
                                        )
                        )
                )

        val pullToRefreshState = rememberPullToRefreshState()

        Scaffold(
                topBar = {
                        TopAppBar(
                                title = {
                                        Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Text(
                                                        text = "Chatspromo Plan",
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                )
                                        }
                                },
                                navigationIcon = {
                                        IconButton(onClick = onBackPressed) {
                                                Icon(
                                                        imageVector =
                                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                        contentDescription = "Back",
                                                        tint = Color.White
                                                )
                                        }
                                },
                                actions = {
                                        IconButton(
                                                onClick = { onSyncPlans() },
                                                enabled = !isSyncing
                                        ) {
                                                if (isSyncing) {
                                                        CircularProgressIndicator(
                                                                color = Color.White,
                                                                strokeWidth = 2.dp,
                                                                modifier = Modifier.size(20.dp)
                                                        )
                                                } else {
                                                        Icon(
                                                                imageVector = Icons.Filled.Refresh,
                                                                contentDescription = "Sync Plans",
                                                                tint = Color.White
                                                        )
                                                }
                                        }
                                        IconButton(onClick = { /* Menu */}) {
                                                Icon(
                                                        imageVector = Icons.Filled.MoreVert,
                                                        contentDescription = "Menu",
                                                        tint = Color.White
                                                )
                                        }
                                },
                                colors =
                                        TopAppBarDefaults.topAppBarColors(
                                                containerColor = Color(0xFF0F172A)
                                        )
                        )
                },
                containerColor = Color(0xFF0F172A)
        ) { innerPadding ->
                PullToRefreshBox(
                        state = pullToRefreshState,
                        isRefreshing = isSyncing,
                        onRefresh = onSyncPlans,
                        modifier = Modifier.padding(innerPadding)
                ) {
                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(horizontal = 20.dp, vertical = 16.dp)
                                                .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                                plans.forEach { plan ->
                                        AccordionPlanCard(
                                                plan = plan,
                                                isExpanded = expandedCardId == plan.id,
                                                onClick = { expandedCardId = plan.id },
                                                onBuyClick = {
                                                        selectedCardId = plan.id
                                                        showPaymentDialog = true
                                                },
                                                onInfoClick = { infoDialogPlan = plan }
                                        )
                                }
                        }
                }
        }

        if (infoDialogPlan != null) {
                Dialog(
                        onDismissRequest = { infoDialogPlan = null },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                        val plan = infoDialogPlan!!
                        val gradient = Brush.verticalGradient(plan.gradientColors)

                        Box(
                                modifier =
                                        Modifier.fillMaxWidth(0.92f)
                                                .fillMaxHeight(0.85f)
                                                .background(
                                                        Color(0xFF1E293B),
                                                        RoundedCornerShape(28.dp)
                                                )
                        ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                        // Header Area with Gradient
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .background(
                                                                        gradient,
                                                                        RoundedCornerShape(
                                                                                topStart = 28.dp,
                                                                                topEnd = 28.dp
                                                                        )
                                                                )
                                                                .padding(
                                                                        vertical = 32.dp,
                                                                        horizontal = 24.dp
                                                                ),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Column(
                                                        horizontalAlignment =
                                                                Alignment.CenterHorizontally,
                                                        verticalArrangement =
                                                                Arrangement.spacedBy(12.dp)
                                                ) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.size(72.dp)
                                                                                .background(
                                                                                        Color.White
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.25f
                                                                                                ),
                                                                                        CircleShape
                                                                                ),
                                                                contentAlignment = Alignment.Center
                                                        ) {
                                                                Icon(
                                                                        imageVector =
                                                                                Icons.Filled.Info,
                                                                        contentDescription = "Info",
                                                                        tint = Color.White,
                                                                        modifier =
                                                                                Modifier.size(40.dp)
                                                                )
                                                        }
                                                        Text(
                                                                text = plan.title,
                                                                color = Color.White,
                                                                fontSize = 28.sp,
                                                                fontWeight = FontWeight.ExtraBold
                                                        )
                                                        Text(
                                                                text = "Plan Features",
                                                                color =
                                                                        Color.White.copy(
                                                                                alpha = 0.9f
                                                                        ),
                                                                fontSize = 16.sp,
                                                                fontWeight = FontWeight.Medium
                                                        )
                                                }
                                        }

                                        // Features List with Scroll
                                        Column(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .weight(1f)
                                                                .padding(horizontal = 20.dp)
                                                                .padding(top = 24.dp)
                                                                .verticalScroll(
                                                                        rememberScrollState()
                                                                ),
                                                verticalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                                plan.planInfo.features.forEach { feature ->
                                                        Row(
                                                                modifier =
                                                                        Modifier.fillMaxWidth()
                                                                                .background(
                                                                                        Color(
                                                                                                0xFF334155
                                                                                        ),
                                                                                        RoundedCornerShape(
                                                                                                12.dp
                                                                                        )
                                                                                )
                                                                                .padding(14.dp),
                                                                verticalAlignment = Alignment.Top
                                                        ) {
                                                                Box(
                                                                        modifier =
                                                                                Modifier.size(24.dp)
                                                                                        .background(
                                                                                                plan.gradientColors
                                                                                                        .first()
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.25f
                                                                                                        ),
                                                                                                CircleShape
                                                                                        ),
                                                                        contentAlignment =
                                                                                Alignment.Center
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        Icons.Filled
                                                                                                .CheckCircle,
                                                                                contentDescription =
                                                                                        "Included",
                                                                                tint =
                                                                                        plan.gradientColors
                                                                                                .first(),
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                16.dp
                                                                                        )
                                                                        )
                                                                }
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.width(
                                                                                        12.dp
                                                                                )
                                                                )
                                                                Text(
                                                                        text = feature,
                                                                        color = Color(0xFFE2E8F0),
                                                                        fontSize = 14.sp,
                                                                        lineHeight = 20.sp,
                                                                        fontWeight =
                                                                                FontWeight.Medium
                                                                )
                                                        }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                        }

                                        // Accept Button
                                        Button(
                                                onClick = { infoDialogPlan = null },
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(horizontal = 20.dp)
                                                                .padding(
                                                                        bottom = 24.dp,
                                                                        top = 16.dp
                                                                )
                                                                .height(56.dp),
                                                colors =
                                                        ButtonDefaults.buttonColors(
                                                                containerColor =
                                                                        plan.gradientColors.first()
                                                        ),
                                                shape = RoundedCornerShape(16.dp),
                                                elevation =
                                                        ButtonDefaults.buttonElevation(
                                                                defaultElevation = 4.dp,
                                                                pressedElevation = 8.dp
                                                        )
                                        ) {
                                                Text(
                                                        text = "Got it!",
                                                        color = Color.White,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold
                                                )
                                        }
                                }
                        }
                }
        }

        if (showPaymentDialog) {
                val selectedPlan = plans.find { it.id == selectedCardId }
                selectedPlan?.let { plan ->
                        PlanPaymentChoiceDialog(
                                title = "${plan.title} Plan",
                                price = plan.price,
                                accent = plan.gradientColors.first(),
                                showOnlyRazorpay = showOnlyRazorpay,
                                onDismiss = { showPaymentDialog = false },
                                onRazorpay = {
                                        showPaymentDialog = false
                                        if (plan.id == "ai_agent") {
                                                activity.launchAIAgentRazorpay("aiagent499")
                                        } else {
                                                activity.launchPremiumCheckout(plan.id)
                                        }
                                },
                                onPlayStore = {
                                        showPaymentDialog = false
                                        if (plan.id == "ai_agent") {
                                                activity.launchAIAgentPlayStore(
                                                        AIBillingManager.PRODUCT_AIAGENT_499
                                                )
                                        } else {
                                                activity.launchPlayStore(plan.id)
                                        }
                                }
                        )
                }
        }
}

data class PlanInfo(val features: List<String>)

data class PriceCardData(
        val id: String,
        val title: String,
        val price: String,
        val period: String,
        val description: String,
        val gradientColors: List<Color>,
        val planInfo: PlanInfo
)

@Composable
fun AccordionPlanCard(
        plan: PriceCardData,
        isExpanded: Boolean,
        onClick: () -> Unit,
        onBuyClick: () -> Unit,
        onInfoClick: () -> Unit
) {
        val backgroundBrush =
                Brush.verticalGradient(colors = plan.gradientColors, startY = 0f, endY = 1200f)
        val shape = RoundedCornerShape(28.dp)

        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(backgroundBrush, shape)
                                .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onClick
                                )
                                .padding(24.dp)
                                .animateContentSize()
        ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                        // Header Row
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Text(
                                        text = plan.title,
                                        color = Color.White,
                                        fontSize = if (plan.id == "ai_agent") 20.sp else 24.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                )

                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                        if (isExpanded) {
                                                Box(
                                                        modifier =
                                                                Modifier.size(40.dp)
                                                                        .background(
                                                                                Color.White.copy(
                                                                                        alpha =
                                                                                                0.25f
                                                                                ),
                                                                                CircleShape
                                                                        )
                                                                        .clickable(
                                                                                onClick =
                                                                                        onInfoClick
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Outlined.Info,
                                                                contentDescription = "Plan Info",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(22.dp)
                                                        )
                                                }
                                        }
                                        Box(
                                                modifier =
                                                        Modifier.size(40.dp)
                                                                .background(
                                                                        Color.White.copy(
                                                                                alpha = 0.25f
                                                                        ),
                                                                        CircleShape
                                                                ),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        imageVector =
                                                                if (isExpanded)
                                                                        Icons.Filled.KeyboardArrowUp
                                                                else Icons.Filled.KeyboardArrowDown,
                                                        contentDescription = "Expand",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                )
                                        }
                                }
                        }

                        if (isExpanded) {
                                Spacer(modifier = Modifier.height(28.dp))

                                // Price Area with Background
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .background(
                                                                Color.White.copy(alpha = 0.15f),
                                                                RoundedCornerShape(20.dp)
                                                        )
                                                        .padding(20.dp)
                                ) {
                                        Row(verticalAlignment = Alignment.Bottom) {
                                                Text(
                                                        text = plan.price,
                                                        color = Color.White,
                                                        fontSize = 40.sp,
                                                        fontFamily = PoppinsFamily,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        lineHeight = 42.sp
                                                )
                                                Text(
                                                        text = " ${plan.period}",
                                                        color = Color.White.copy(alpha = 0.95f),
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                )
                                        }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Description Area
                                Text(
                                        text = plan.description,
                                        color = Color.White.copy(alpha = 0.95f),
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(28.dp))

                                // Buy Button
                                Button(
                                        onClick = onBuyClick,
                                        modifier = Modifier.fillMaxWidth().height(58.dp),
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = Color.White
                                                ),
                                        shape = RoundedCornerShape(16.dp),
                                        elevation =
                                                ButtonDefaults.buttonElevation(
                                                        defaultElevation = 6.dp,
                                                        pressedElevation = 10.dp
                                                )
                                ) {
                                        Text(
                                                text = "BUY NOW",
                                                color = plan.gradientColors.first(),
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 1.sp
                                        )
                                }
                        }
                }
        }
}
