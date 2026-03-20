package com.message.bulksend.plan

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.message.bulksend.auth.UserManager
import com.message.bulksend.referral.ReferralManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

class BillingManager(
    private val context: Context,
    private val onPurchaseSuccess: (Purchase) -> Unit,
    private val onPurchaseFailure: (String) -> Unit
) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val userManager = UserManager(context)
    private val referralManager = ReferralManager(context)
    private var billingClient: BillingClient? = null
    private var productDetailsList = mutableListOf<ProductDetails>()
    private var onProductsLoadedCallback: ((Map<String, String>) -> Unit)? = null
    
    companion object {
        private const val TAG = "BillingManager"
        
        // Product IDs - Replace with your actual product IDs from Play Console
        const val PRODUCT_MONTHLY = "monthly_premium"
        const val PRODUCT_YEARLY = "yearly_premium"
        const val PRODUCT_LIFETIME = "lifetime_premium"
    }

    // Initialize billing client
    fun initialize() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection() // Auto reconnect feature
            .build()
        
        connectToGooglePlay()
    }

    // Purchase update listener
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

    // Connect to Google Play
    private fun connectToGooglePlay() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected successfully")
                    queryProducts()
                    queryPurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d(TAG, "Billing service disconnected")
                // Connection will auto-reconnect due to enableAutoServiceReconnection()
            }
        })
    }

    // Query available products (Consumable)
    private fun queryProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_MONTHLY)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_YEARLY)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_LIFETIME)
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
                        Log.d(TAG, "Products loaded: ${productDetailsList.size}")
                        
                        // Create price map
                        val priceMap = mutableMapOf<String, String>()
                        productDetailsList.forEach { product ->
                            val price = product.oneTimePurchaseOfferDetails?.formattedPrice ?: "N/A"
                            priceMap[product.productId] = price
                            Log.d(TAG, "Product: ${product.productId}, Price: $price")
                        }
                        
                        // Notify callback with prices
                        withContext(Dispatchers.Main) {
                            onProductsLoadedCallback?.invoke(priceMap)
                        }
                    }
                    else -> {
                        Log.e(TAG, "Failed to query products: ${result.billingResult.debugMessage}")
                        withContext(Dispatchers.Main) {
                            onProductsLoadedCallback?.invoke(emptyMap())
                        }
                    }
                }
            }
        }
    }

    // Query existing purchases
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
                        Log.e(TAG, "Failed to query purchases: ${purchasesResult.billingResult.debugMessage}")
                    }
                }
            }
        }
    }

    // Launch purchase flow
    fun launchPurchaseFlow(activity: Activity, productId: String) {
        val productDetails = productDetailsList.find { it.productId == productId }
        
        if (productDetails == null) {
            Log.e(TAG, "Product not found: $productId")
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
            Log.e(TAG, "Failed to launch billing flow: ${billingResult?.debugMessage}")
            onPurchaseFailure("Failed to start purchase")
        }
    }

    // Handle purchase
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Verify purchase on your server here before granting entitlement
            
            if (!purchase.isAcknowledged) {
                // For consumable products, consume the purchase
                consumePurchase(purchase)
            }
        }
    }

    // Consume purchase (for consumable products)
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
                        Log.d(TAG, "Purchase consumed successfully")
                        
                        // Update Firebase with purchase details
                        val productId = purchase.products.firstOrNull() ?: ""
                        val planType = when (productId) {
                            PRODUCT_MONTHLY -> "monthly"
                            PRODUCT_YEARLY -> "yearly"
                            PRODUCT_LIFETIME -> "lifetime"
                            else -> "monthly"
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
                        Log.e(TAG, "Failed to consume purchase: ${result.billingResult.debugMessage}")
                        withContext(Dispatchers.Main) {
                            onPurchaseFailure("Failed to process purchase")
                        }
                    }
                }
            }
        }
    }
    
    // Update Firebase after successful purchase
    private suspend fun updateFirebaseAfterPurchase(
        planType: String,
        purchaseToken: String,
        orderId: String
    ) {
        try {
            val userEmail = auth.currentUser?.email
            val userId = auth.currentUser?.uid
            if (userEmail == null) {
                Log.e(TAG, "User email not found")
                return
            }
            
            val currentTime = Timestamp.now()
            
            // Calculate end date based on plan type
            val daysToAdd = when (planType) {
                "monthly" -> 30L
                "yearly" -> 365L
                "lifetime" -> 36500L // 100 years for lifetime
                else -> 30L
            }
            
            val endTime = Timestamp(currentTime.seconds + (daysToAdd * 24 * 60 * 60), 0)
            
            // Update user subscription in Firebase
            val userData = userManager.getUserData(userEmail)
            if (userData != null) {
                firestore.collection("email_data")
                    .document(userEmail)
                    .update(
                        mapOf(
                            "subscriptionType" to "premium",
                            "planType" to planType, // monthly or lifetime
                            "subscriptionStartDate" to currentTime,
                            "subscriptionEndDate" to endTime,
                            "contactsLimit" to -1, // -1 means unlimited
                            "groupsLimit" to -1, // -1 means unlimited
                            "lastPurchaseToken" to purchaseToken,
                            "lastOrderId" to orderId,
                            "lastPaymentDate" to currentTime,
                            "paymentMethod" to "google_play",
                            "source" to "playstore"
                        )
                    )
                    .await()
                
                Log.d(TAG, "Firebase updated successfully for user: $userEmail with plan: $planType")
                
                // Also update userDetails collection with plan info
                if (userId != null) {
                    updateUserDetailsPlan(userId, planType, currentTime, endTime, orderId, purchaseToken, "google_play")
                    
                    // Process referral reward for main app plans (NOT AI plans)
                    // monthly, yearly, lifetime qualify for referral rewards
                    processReferralRewardForPurchase(planType)
                }
                
                // Fetch updated data and save to SharedPreferences
                val updatedUserData = userManager.getUserData(userEmail)
                if (updatedUserData != null) {
                    saveSubscriptionPreferences(updatedUserData)
                    Log.d(TAG, "SharedPreferences updated successfully")
                }
            } else {
                Log.e(TAG, "User data not found for: $userEmail")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Firebase after purchase", e)
        }
    }
    
    // Update userDetails collection with plan info
    private suspend fun updateUserDetailsPlan(
        userId: String,
        planType: String,
        startDate: Timestamp,
        endDate: Timestamp,
        orderId: String,
        purchaseToken: String,
        paymentMethod: String
    ) {
        try {
            val planData = mapOf(
                "subscriptionType" to "premium",
                "planType" to planType,
                "subscriptionStartDate" to startDate,
                "subscriptionEndDate" to endDate,
                "lastOrderId" to orderId,
                "lastPurchaseToken" to purchaseToken,
                "lastPaymentDate" to startDate,
                "paymentMethod" to paymentMethod
            )
            
            firestore.collection("userDetails")
                .document(userId)
                .update(planData)
                .await()
            
            Log.d(TAG, "✅ userDetails updated with plan info for userId: $userId")
        } catch (e: Exception) {
            // If document doesn't exist or update fails, try to set with merge
            try {
                val planData = mapOf(
                    "subscriptionType" to "premium",
                    "planType" to planType,
                    "subscriptionStartDate" to startDate,
                    "subscriptionEndDate" to endDate,
                    "lastOrderId" to orderId,
                    "lastPurchaseToken" to purchaseToken,
                    "lastPaymentDate" to startDate,
                    "paymentMethod" to paymentMethod
                )
                
                firestore.collection("userDetails")
                    .document(userId)
                    .set(planData, com.google.firebase.firestore.SetOptions.merge())
                    .await()
                
                Log.d(TAG, "✅ userDetails created/merged with plan info for userId: $userId")
            } catch (e2: Exception) {
                Log.e(TAG, "❌ Failed to update userDetails for userId: $userId", e2)
            }
        }
    }
    
    // Save subscription preferences to SharedPreferences
    private fun saveSubscriptionPreferences(userData: com.message.bulksend.data.UserData) {
        try {
            val sharedPref = context.getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("subscription_type", userData.subscriptionType)
                putInt("contacts_limit", userData.contactsLimit)
                putInt("current_contacts", userData.currentContactsCount)
                putInt("groups_limit", userData.groupsLimit)
                putInt("current_groups", userData.currentGroupsCount)
                putString("user_email", userData.email)

                // Save expiry info for premium users
                if (userData.subscriptionType == "premium") {
                    userData.subscriptionEndDate?.let { endDate ->
                        putLong("subscription_end_time", endDate.seconds * 1000)
                    }
                } else {
                    remove("subscription_end_time")
                }

                apply()
            }

            Log.d(TAG, "✅ Subscription preferences saved:")
            Log.d(TAG, "  Type: ${userData.subscriptionType}")
            Log.d(TAG, "  Contacts: ${userData.currentContactsCount}/${userData.contactsLimit}")
            Log.d(TAG, "  Groups: ${userData.currentGroupsCount}/${userData.groupsLimit}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving subscription preferences", e)
        }
    }

    // Set callback for when products are loaded
    fun setOnProductsLoadedListener(callback: (Map<String, String>) -> Unit) {
        onProductsLoadedCallback = callback
        
        // If products already loaded, call callback immediately
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
    
    // Get product price by ID
    fun getProductPrice(productId: String): String {
        val product = productDetailsList.find { it.productId == productId }
        return product?.oneTimePurchaseOfferDetails?.formattedPrice ?: "N/A"
    }
    
    // Get all product prices
    fun getAllProductPrices(): Map<String, String> {
        val priceMap = mutableMapOf<String, String>()
        productDetailsList.forEach { product ->
            val price = product.oneTimePurchaseOfferDetails?.formattedPrice ?: "N/A"
            priceMap[product.productId] = price
        }
        return priceMap
    }

    // End billing connection
    fun endConnection() {
        billingClient?.endConnection()
        Log.d(TAG, "Billing connection ended")
    }
    
    // Process referral reward for plan purchases, including AI agent plans
    private fun processReferralRewardForPurchase(planType: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val purchaseAmount = when (planType) {
                    "monthly" -> 299
                    "yearly" -> 1499
                    "lifetime" -> 2999
                    "aiagent499" -> 499
                    "ai_monthly" -> 199
                    "ai_yearly" -> 899
                    else -> 0
                }

                if (purchaseAmount <= 0) {
                    Log.d(TAG, "Plan amount not configured for referral reward: $planType")
                    return@launch
                }
                
                val result = referralManager.processReferralReward(planType, purchaseAmount)
                if (result.success) {
                    Log.d(TAG, "✅ Referral reward processed: ₹${result.commission}")
                } else {
                    Log.d(TAG, "Referral reward not applicable: ${result.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing referral reward", e)
            }
        }
    }
}
