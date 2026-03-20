# Plan Package - Subscription & Billing System

## 📋 Overview

Yeh package app ki complete subscription aur billing system ko handle karta hai. Ismein Google Play Billing aur Razorpay payment integration hai, jo main app aur ChatsPromo AI dono ke liye alag-alag plans provide karta hai.

---

## 📁 Package Structure

```
com.message.bulksend.plan/
├── AIBillingManager.kt          # ChatsPromo AI ke liye Google Play Billing
├── BillingManager.kt            # Main app ke liye Google Play Billing
├── ChatsPromoAIPlanActivity.kt  # ChatsPromo AI subscription UI
├── GetActivity.kt               # Razorpay payment gateway activity
└── PrePackActivity.kt           # Main app premium plans UI
```

---

## 🔧 Files Detail

### 1. **AIBillingManager.kt**

**Purpose:** ChatsPromo AI ke liye Google Play Billing handle karta hai.

**Key Features:**
- ✅ AI-specific product IDs manage karta hai
- ✅ Google Play se purchase flow handle karta hai
- ✅ Purchase ko consume karke Firebase update karta hai
- ✅ SharedPreferences mein AI subscription save karta hai

**Product IDs:**
```kotlin
PRODUCT_AI_MONTHLY = "ai_monthly_premium"  // ₹199/month
PRODUCT_AI_YEARLY = "ai_yearly_premium"    // ₹899/year
```

**Main Functions:**
- `initialize()` - Billing client setup
- `launchPurchaseFlow()` - Purchase dialog open karta hai
- `handlePurchase()` - Purchase success handle karta hai
- `updateFirebaseAfterPurchase()` - Firebase mein AI subscription update karta hai
- `saveAISubscriptionPreferences()` - Local storage mein save karta hai

**Firebase Collection:**
```
chatspromo_ai_subscriptions/{email_with_underscore}/
├── email
├── subscriptionType: "premium"
├── planType: "ai_monthly" | "ai_yearly"
├── subscriptionStartDate
├── subscriptionEndDate
├── lastPurchaseToken
├── lastOrderId
├── paymentMethod: "google_play"
└── isActive: true
```

**Important Notes:**
- ⚠️ AI plans referral system mein include NAHI hain
- ⚠️ Consumable products use karta hai (repeat purchase ke liye)
- ⚠️ Auto-reconnection enabled hai

---

### 2. **BillingManager.kt**

**Purpose:** Main app (WhatsAuto) ke liye Google Play Billing handle karta hai.

**Key Features:**
- ✅ Main app ke 3 plans manage karta hai
- ✅ Referral system integration hai
- ✅ UserDetails collection update karta hai
- ✅ Unlimited contacts/groups access deta hai

**Product IDs:**
```kotlin
PRODUCT_MONTHLY = "monthly_premium"   // ₹149/month
PRODUCT_YEARLY = "yearly_premium"     // ₹999/year
PRODUCT_LIFETIME = "lifetime_premium" // ₹1,499 (one-time)
```

**Main Functions:**
- `initialize()` - Billing setup
- `launchPurchaseFlow()` - Purchase start karta hai
- `consumePurchase()` - Purchase consume karta hai
- `updateFirebaseAfterPurchase()` - Firebase update karta hai
- `updateUserDetailsPlan()` - userDetails collection update karta hai
- `processReferralRewardForPurchase()` - Referral commission process karta hai

**Firebase Collections Updated:**
```
email_data/{email}/
├── subscriptionType: "premium"
├── planType: "monthly" | "yearly" | "lifetime"
├── subscriptionStartDate
├── subscriptionEndDate
├── contactsLimit: -1 (unlimited)
├── groupsLimit: -1 (unlimited)
├── lastPurchaseToken
├── lastOrderId
├── paymentMethod: "google_play"
└── source: "playstore"

userDetails/{userId}/
├── subscriptionType
├── planType
├── subscriptionStartDate
├── subscriptionEndDate
├── lastOrderId
├── lastPurchaseToken
└── paymentMethod
```

**Referral System:**
- ✅ Monthly, Yearly, Lifetime plans qualify karte hain
- ❌ AI plans (ai_monthly, ai_yearly) qualify NAHI karte
- Commission calculation:
  - Monthly: ₹149
  - Yearly: ₹999
  - Lifetime: ₹1,499

---

### 3. **ChatsPromoAIPlanActivity.kt**

**Purpose:** ChatsPromo AI subscription purchase ke liye complete UI aur payment flow.

**Key Features:**
- ✅ Modern gradient-based UI design
- ✅ Dual payment options (Razorpay + Play Store)
- ✅ Real-time price loading from Play Store
- ✅ Payment success/failure handling
- ✅ Auto-sync after purchase

**UI Components:**
- `ChatsPromoAIPlanScreen` - Main screen with plans
- `PlanCard` - Individual plan card with features
- `FeatureChip` - Horizontal scrollable feature badges
- `PaymentMethodDialog` - Payment option selector

**Plans Display:**
```
Monthly Plan:
├── Price: ₹199/month
├── Original: ₹399 (50% OFF)
└── Features: Unlimited AI, Auto Reply, Priority Support

Yearly Plan (BEST VALUE):
├── Price: ₹899/year
├── Original: ₹2,388 (62% OFF)
├── Save: ₹1,489/year
└── Features: All Monthly + Best Value
```

**Payment Flow:**
1. User selects plan
2. Payment dialog shows (Razorpay/Play Store)
3. Payment processing
4. Firebase update
5. Sync subscription data
6. Success message + activity close

**Razorpay Integration:**
- Cloud Function: `createAIOrder`
- Key ID: `rzp_live_RTIlARYCEbxgfS`
- Prefilled email (readonly)
- Custom theme color: `#667eea`

**Important Notes:**
- ⚠️ AI plans referral mein include NAHI
- ⚠️ Separate Firebase collection use karta hai
- ⚠️ Auto-sync after purchase for instant activation

---

### 4. **GetActivity.kt**

**Purpose:** Main app ke liye Razorpay payment gateway activity.

**Key Features:**
- ✅ Razorpay checkout integration
- ✅ Order creation via Cloud Function
- ✅ Payment verification
- ✅ Firebase update after success
- ✅ Referral reward processing

**UI Components:**
- `GetActivityScreen` - Payment gateway screen
- Loading state with order creation
- Success/failure handling

**Payment Flow:**
```
1. LaunchedEffect → createOrder()
2. Order created → Display order details
3. User clicks "Proceed to Payment"
4. Razorpay checkout opens
5. Payment success → onPaymentSuccess()
6. Update Firebase → email_data + userDetails
7. Process referral reward
8. Save to SharedPreferences
9. Close activity
```

**Cloud Functions:**
```kotlin
CREATE_ORDER_URL = "https://us-central1-mailtracker-demo.cloudfunctions.net/createOrder"
VERIFY_PAYMENT_URL = "https://us-central1-mailtracker-demo.cloudfunctions.net/verifyPayment"
```

**Order Response:**
```kotlin
data class OrderResponse(
    val success: Boolean,
    val orderId: String,
    val amount: Int,
    val currency: String,
    val keyId: String,
    val planName: String
)
```

**Referral Processing:**
- ✅ Main app plans qualify (monthly, yearly, lifetime)
- ❌ AI plans do NOT qualify
- Commission based on purchase amount
- Automatic processing after successful payment

---

### 5. **PrePackActivity.kt**

**Purpose:** Main app premium plans ka complete UI aur purchase flow.

**Key Features:**
- ✅ Beautiful gradient-based design
- ✅ Horizontal scrollable plan cards
- ✅ Dual payment options
- ✅ Real-time Play Store prices
- ✅ Refresh subscription button
- ✅ Feature comparison table

**UI Components:**
- `GetPremiumScreen` - Main premium screen
- `ColorfulPlanCard` - Vibrant gradient plan cards
- `PaymentDialog` - Payment method selector
- `ShimmerContinueButton` - Animated continue button
- `FeatureRow` - Feature comparison rows

**Plans Display:**
```
Monthly Plan (Purple Gradient):
├── Price: ₹149/month
├── Features: Unlimited Messages, Extract Contacts, Reports
└── Gradient: #667EEA → #764BA2

Yearly Plan (Orange Gradient):
├── Price: ₹999/year
├── Features: All Monthly features
└── Gradient: #FF6B6B → #FF8E53

Lifetime Plan (Green Gradient) - MOST POPULAR:
├── Price: ₹1,499 (one-time)
├── Features: All features forever
├── Badge: ⭐ BEST VALUE
└── Gradient: #11998E → #38EF7D
```

**Feature Comparison:**
```
Feature                    | Free | Premium
---------------------------|------|--------
Remove Ads                 | ✗    | ✓
Message Unknown Contact    | ✓    | ✓
Number of Campaigns        | 01   | ∞
Export Campaign            | ✓    | ✓
Maximum Contacts           | 10   | ∞
Import Sheet/CSV/WP Group  | ✓    | ✓
Maximum Groups             | 5    | ∞
Unique Identity            | ✓    | ✓
Random Delay               | ✓    | ✓
Export Report              | ✓    | ✓
```

**Special Features:**
- 🔄 Refresh button - Subscription data sync karta hai
- 📊 Scroll indicators - Plan navigation ke liye dots
- ✨ Shimmer animation - Continue button par
- 🎨 Gradient cards - Eye-catching design

**Payment Dialog:**
- Play Store option (with icon)
- Razorpay option (India only)
- Clean card-based design

**Refresh Functionality:**
```kotlin
fun refreshSubscriptionData(onComplete: (Boolean) -> Unit)
```
- Firebase se latest data fetch karta hai
- SharedPreferences update karta hai
- Success/failure toast show karta hai
- Activity close karta hai on success

---

## 🔄 Payment Flow Comparison

### Google Play Billing Flow:
```
1. User selects plan
2. BillingManager.launchPurchaseFlow()
3. Google Play dialog opens
4. User completes payment
5. purchasesUpdatedListener triggered
6. handlePurchase() → consumePurchase()
7. updateFirebaseAfterPurchase()
8. saveSubscriptionPreferences()
9. onPurchaseSuccess callback
10. Activity closes
```

### Razorpay Flow:
```
1. User selects plan
2. createOrder() API call
3. Order created with orderId
4. launchRazorpayCheckout()
5. Razorpay UI opens
6. User completes payment
7. onPaymentSuccess() triggered
8. updateUserPlanInFirebase()
9. processReferralReward()
10. saveSubscriptionPreferences()
11. Activity closes
```

---

## 💾 Data Storage

### SharedPreferences (Main App):
```kotlin
"subscription_prefs"
├── subscription_type: "free" | "premium"
├── contacts_limit: Int (-1 = unlimited)
├── current_contacts: Int
├── groups_limit: Int (-1 = unlimited)
├── current_groups: Int
├── user_email: String
└── subscription_end_time: Long (milliseconds)
```

### SharedPreferences (AI):
```kotlin
"ai_subscription_prefs"
├── ai_subscription_type: "premium"
├── ai_plan_type: "ai_monthly" | "ai_yearly"
├── ai_user_email: String
├── ai_subscription_end_time: Long
└── ai_is_active: Boolean
```

---

## 🎯 Key Differences: Main App vs AI Plans

| Feature | Main App Plans | AI Plans |
|---------|---------------|----------|
| **Product IDs** | monthly_premium, yearly_premium, lifetime_premium | ai_monthly_premium, ai_yearly_premium |
| **Prices** | ₹149, ₹999, ₹1,499 | ₹199, ₹899 |
| **Firebase Collection** | email_data, userDetails | chatspromo_ai_subscriptions |
| **Referral Eligible** | ✅ Yes | ❌ No |
| **Features** | Unlimited contacts/groups | Unlimited AI responses |
| **SharedPrefs** | subscription_prefs | ai_subscription_prefs |
| **Activities** | PrePackActivity, GetActivity | ChatsPromoAIPlanActivity |

---

## 🔐 Security Features

1. **Purchase Verification:**
   - Google Play: Automatic verification via BillingClient
   - Razorpay: Server-side verification via Cloud Function

2. **Token Management:**
   - Purchase tokens stored in Firebase
   - Order IDs tracked for reference
   - Payment method recorded

3. **Duplicate Prevention:**
   - Consumable products prevent duplicate purchases
   - Firebase checks before granting access
   - SharedPreferences validation

---

## 🚀 Usage Examples

### Launch Main App Premium Screen:
```kotlin
val intent = Intent(context, PrepackActivity::class.java)
startActivity(intent)
```

### Launch AI Subscription Screen:
```kotlin
val intent = Intent(context, ChatsPromoAIPlanActivity::class.java)
startActivityForResult(intent, AI_PLAN_REQUEST_CODE)
```

### Launch Razorpay Payment:
```kotlin
val intent = Intent(context, GetActivity::class.java).apply {
    putExtra("SELECTED_PLAN", "lifetime") // or "monthly", "yearly"
}
startActivityForResult(intent, PAYMENT_REQUEST_CODE)
```

### Check Subscription Status:
```kotlin
val sharedPref = getSharedPreferences("subscription_prefs", MODE_PRIVATE)
val subscriptionType = sharedPref.getString("subscription_type", "free")
val isPremium = subscriptionType == "premium"
```

---

## 📱 UI Design Highlights

### Color Scheme:
- **Background:** Dark gradient (#1A1A2E → #16213E)
- **Primary:** Cyan (#00D4FF)
- **Success:** Green (#10B981)
- **Purple:** #667EEA → #764BA2
- **Orange:** #FF6B6B → #FF8E53
- **Green:** #11998E → #38EF7D

### Animations:
- ✨ Shimmer effect on buttons
- 🎨 Gradient transitions
- 📊 Scroll indicators
- 🔄 Loading states

### Typography:
- **Headers:** 22-24sp, ExtraBold
- **Prices:** 26-36sp, Bold
- **Body:** 13-16sp, Medium
- **Captions:** 11-12sp, Regular

---

## 🐛 Error Handling

### Common Errors:
1. **Billing Connection Failed:**
   - Auto-reconnection enabled
   - User-friendly error messages

2. **Payment Failed:**
   - Clear error display
   - Retry option available

3. **Firebase Update Failed:**
   - Fallback to merge operation
   - Error logging for debugging

4. **Network Issues:**
   - Timeout handling (30 seconds)
   - Retry mechanism

---

## 📝 Important Notes

1. **Referral System:**
   - Only main app plans qualify
   - AI plans do NOT give referral rewards
   - Commission processed automatically

2. **Subscription Duration:**
   - Monthly: 30 days
   - Yearly: 365 days
   - Lifetime: 100 years (36,500 days)

3. **Unlimited Access:**
   - contactsLimit: -1
   - groupsLimit: -1
   - Checked in app logic

4. **Testing:**
   - Use test product IDs for development
   - Test both payment methods
   - Verify Firebase updates

---

## 🔗 Dependencies

```kotlin
// Google Play Billing
implementation("com.android.billingclient:billing-ktx:6.0.1")

// Razorpay
implementation("com.razorpay:checkout:1.6.33")

// Firebase
implementation("com.google.firebase:firebase-auth-ktx")
implementation("com.google.firebase:firebase-firestore-ktx")

// Networking
implementation("com.squareup.okhttp3:okhttp:4.11.0")

// Compose UI
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-extended")
```

---

## 👨‍💻 Maintained By

WhatsAuto Development Team

**Last Updated:** January 2026

---

## 📞 Support

For any issues or queries related to billing and subscriptions, contact the development team.

---

**Note:** Yeh README file plan package ki complete documentation hai. Har file ka purpose, functionality, aur integration detail mein explain kiya gaya hai.
