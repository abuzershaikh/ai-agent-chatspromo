package com.message.bulksend.plan

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.message.bulksend.referral.ReferralManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

    /**
     * AIBillingManager - Handles Google Play Billing for ChatsPromo AI subscriptions
     * Products: aiagent499 (₹499), ai_monthly_premium (₹199), ai_yearly_premium (₹899)
     */
class AIBillingManager(
    private val context: Context,
    private val onPurchaseSuccess: (Purchase) -> Unit,
    private val onPurchaseFailure: (String) -> Unit
) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val referralManager by lazy { ReferralManager(context) }
    private var billingClient: BillingClient? = null
    private var productDetailsList = mutableListOf<ProductDetails>()
    private var onProductsLoadedCallback: ((Map<String, String>) -> Unit)? = null
    
    companion object {
        private const val TAG = "AIBillingManager"
        
        // ChatsPromo AI Product IDs - Replace with your actual product IDs from Play Console
        const val PRODUCT_AIAGENT_499 = "aiagent499"
        const val PRODUCT_AI_MONTHLY = "ai_monthly_premium"
        const val PRODUCT_AI_YEARLY = "ai_yearly_premium"
    }

    fun initialize() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
        
        connectToGooglePlay()
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let { purchaseList ->
                    for (purchase in purchaseList) {
                        handlePurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User canceled the purchase")
                onPurchaseFailure("Purchase canceled by user")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.d(TAG, "Item already owned")
                onPurchaseFailure("You already own this item")
            }
            else -> {
                Log.e(TAG, "Purchase failed: ${billingResult.debugMessage}")
                onPurchaseFailure("Purchase failed: ${billingResult.debugMessage}")
            }
        }
    }


    private fun connectToGooglePlay() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "AI Billing client connected successfully")
                    queryProducts()
                    queryPurchases()
                } else {
                    Log.e(TAG, "AI Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d(TAG, "AI Billing service disconnected")
            }
        })
    }

    private fun queryProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_AIAGENT_499)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_AI_MONTHLY)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_AI_YEARLY)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            val productDetailsResult = withContext(Dispatchers.IO) {
                billingClient?.queryProductDetails(params)
            }

            productDetailsResult?.let { result ->
                when (result.billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        productDetailsList.clear()
                        result.productDetailsList?.let { list ->
                            productDetailsList.addAll(list)
                        }
                        Log.d(TAG, "AI Products loaded: ${productDetailsList.size}")
                        
                        val priceMap = mutableMapOf<String, String>()
                        productDetailsList.forEach { product ->
                            val price = product.oneTimePurchaseOfferDetails?.formattedPrice ?: "N/A"
                            priceMap[product.productId] = price
                            Log.d(TAG, "AI Product: ${product.productId}, Price: $price")
                        }
                        
                        withContext(Dispatchers.Main) {
                            onProductsLoadedCallback?.invoke(priceMap)
                        }
                    }
                    else -> {
                        Log.e(TAG, "Failed to query AI products: ${result.billingResult.debugMessage}")
                        withContext(Dispatchers.Main) {
                            onProductsLoadedCallback?.invoke(emptyMap())
                        }
                    }
                }
            }
        }
    }

    private fun queryPurchases() {
        billingClient?.let { client ->
            CoroutineScope(Dispatchers.IO).launch {
                val purchasesResult = client.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )

                when (purchasesResult.billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        purchasesResult.purchasesList.forEach { purchase ->
                            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                                if (!purchase.isAcknowledged) {
                                    handlePurchase(purchase)
                                }
                            }
                        }
                    }
                    else -> {
                        Log.e(TAG, "Failed to query AI purchases: ${purchasesResult.billingResult.debugMessage}")
                    }
                }
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String) {
        val productDetails = productDetailsList.find { it.productId == productId }
        
        if (productDetails == null) {
            Log.e(TAG, "AI Product not found: $productId")
            onPurchaseFailure("Product not available")
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient?.launchBillingFlow(activity, billingFlowParams)
        
        if (billingResult?.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "Failed to launch AI billing flow: ${billingResult?.debugMessage}")
            onPurchaseFailure("Failed to start purchase")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                consumePurchase(purchase)
            }
        }
    }

    private fun consumePurchase(purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            val consumeResult = withContext(Dispatchers.IO) {
                billingClient?.consumePurchase(consumeParams)
            }

            consumeResult?.let { result ->
                when (result.billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        Log.d(TAG, "AI Purchase consumed successfully")
                        
                        val productId = purchase.products.firstOrNull() ?: ""
                        val planType = when (productId) {
                            PRODUCT_AIAGENT_499 -> "aiagent499"
                            PRODUCT_AI_MONTHLY -> "ai_monthly"
                            PRODUCT_AI_YEARLY -> "ai_yearly"
                            else -> "ai_monthly"
                        }
                        
                        updateFirebaseAfterPurchase(
                            planType = planType,
                            purchaseToken = purchase.purchaseToken,
                            orderId = purchase.orderId ?: ""
                        )
                        
                        withContext(Dispatchers.Main) {
                            onPurchaseSuccess(purchase)
                        }
                    }
                    else -> {
                        Log.e(TAG, "Failed to consume AI purchase: ${result.billingResult.debugMessage}")
                        withContext(Dispatchers.Main) {
                            onPurchaseFailure("Failed to process purchase")
                        }
                    }
                }
            }
        }
    }


    private suspend fun updateFirebaseAfterPurchase(
        planType: String,
        purchaseToken: String,
        orderId: String
    ) {
        try {
            val userEmail = auth.currentUser?.email
            if (userEmail == null) {
                Log.e(TAG, "User email not found")
                return
            }
            
            val currentTime = Timestamp.now()
            
            val daysToAdd = when (planType) {
                "aiagent499" -> 30L
                "ai_monthly" -> 30L
                "ai_yearly" -> 365L
                else -> 30L
            }
            
            val endTime = Timestamp(currentTime.seconds + (daysToAdd * 24 * 60 * 60), 0)
            
            // Update AI subscription in Firebase
            firestore.collection("chatspromo_ai_subscriptions")
                .document(userEmail.replace(".", "_"))
                .set(
                    mapOf(
                        "email" to userEmail,
                        "subscriptionType" to "premium",
                        "planType" to planType,
                        "subscriptionStartDate" to currentTime,
                        "subscriptionEndDate" to endTime,
                        "lastPurchaseToken" to purchaseToken,
                        "lastOrderId" to orderId,
                        "lastPaymentDate" to currentTime,
                        "paymentMethod" to "google_play",
                        "isActive" to true
                    )
                )
                .await()
            
            Log.d(TAG, "AI Firebase updated successfully for user: $userEmail with plan: $planType")

            processReferralRewardForAIPurchase(planType)
            
            // Save to SharedPreferences
            saveAISubscriptionPreferences(userEmail, planType, endTime)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating AI Firebase after purchase", e)
        }
    }

    private fun processReferralRewardForAIPurchase(planType: String) {
        val purchaseAmount = when (planType) {
            "aiagent499" -> 499
            "ai_monthly" -> 199
            "ai_yearly" -> 899
            else -> 0
        }

        if (purchaseAmount <= 0) {
            Log.d(TAG, "AI plan amount not configured for referral reward: $planType")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = referralManager.processReferralReward(planType, purchaseAmount)
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
    
    private fun saveAISubscriptionPreferences(email: String, planType: String, endTime: Timestamp) {
        try {
            val sharedPref = context.getSharedPreferences("ai_subscription_prefs", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("ai_subscription_type", "premium")
                putString("ai_plan_type", planType)
                putString("ai_user_email", email)
                putLong("ai_subscription_end_time", endTime.seconds * 1000)
                putBoolean("ai_is_active", true)
                apply()
            }
            Log.d(TAG, "✅ AI Subscription preferences saved")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving AI subscription preferences", e)
        }
    }

    fun setOnProductsLoadedListener(callback: (Map<String, String>) -> Unit) {
        onProductsLoadedCallback = callback
        
        if (productDetailsList.isNotEmpty()) {
            val priceMap = mutableMapOf<String, String>()
            productDetailsList.forEach { product ->
                val price = product.oneTimePurchaseOfferDetails?.formattedPrice ?: "N/A"
                priceMap[product.productId] = price
            }
            callback(priceMap)
        }
    }

    fun refreshProducts() {
        if (billingClient?.isReady == true) {
            queryProducts()
        } else {
            connectToGooglePlay()
        }
    }
    
    fun getProductPrice(productId: String): String {
        val product = productDetailsList.find { it.productId == productId }
        return product?.oneTimePurchaseOfferDetails?.formattedPrice ?: "N/A"
    }

    fun endConnection() {
        billingClient?.endConnection()
        Log.d(TAG, "AI Billing connection ended")
    }
}
