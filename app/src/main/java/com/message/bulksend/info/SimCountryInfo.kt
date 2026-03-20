package com.message.bulksend.info

import android.content.Context
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import org.json.JSONObject

/**
 * SIM Country Info - Detects SIM card country using MCC (Mobile Country Code)
 */
data class CountryInfo(
    val mcc: String,
    val country: String,
    val iso: String,
    val countryCode: String
)

data class SimInfo(
    val simSlot: Int,
    val carrierName: String,
    val mcc: String,
    val mnc: String,
    val countryInfo: CountryInfo?
)

class SimCountryDetector(private val context: Context) {

    private var mccDataMap: Map<String, CountryInfo> = emptyMap()

    init {
        loadMccData()
    }

    /**
     * Load MCC data from assets JSON file
     */
    private fun loadMccData() {
        try {
            val jsonString = context.assets.open("mcc_country_codes.json")
                .bufferedReader()
                .use { it.readText() }

            val jsonObject = JSONObject(jsonString)
            val mccArray = jsonObject.getJSONArray("mcc_data")
            val tempMap = mutableMapOf<String, CountryInfo>()

            for (i in 0 until mccArray.length()) {
                val item = mccArray.getJSONObject(i)
                val mcc = item.getString("mcc")
                val countryInfo = CountryInfo(
                    mcc = mcc,
                    country = item.getString("country"),
                    iso = item.getString("iso"),
                    countryCode = item.getString("countryCode")
                )
                tempMap[mcc] = countryInfo
            }
            mccDataMap = tempMap
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get country info by MCC code
     */
    fun getCountryByMcc(mcc: String): CountryInfo? {
        return mccDataMap[mcc]
    }

    /**
     * Get current SIM's MCC
     */
    fun getCurrentSimMcc(): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val networkOperator = telephonyManager.networkOperator
            if (networkOperator.isNotEmpty() && networkOperator.length >= 3) {
                networkOperator.substring(0, 3)
            } else {
                telephonyManager.simOperator?.takeIf { it.length >= 3 }?.substring(0, 3)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get current SIM's MNC
     */
    fun getCurrentSimMnc(): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val networkOperator = telephonyManager.networkOperator
            if (networkOperator.isNotEmpty() && networkOperator.length >= 5) {
                networkOperator.substring(3)
            } else {
                telephonyManager.simOperator?.takeIf { it.length >= 5 }?.substring(3)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get current SIM's country info
     */
    fun getCurrentSimCountry(): CountryInfo? {
        val mcc = getCurrentSimMcc()
        return mcc?.let { getCountryByMcc(it) }
    }

    /**
     * Get carrier name
     */
    fun getCarrierName(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.networkOperatorName ?: telephonyManager.simOperatorName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Get all SIM cards info (for dual SIM devices)
     */
    fun getAllSimInfo(): List<SimInfo> {
        val simList = mutableListOf<SimInfo>()
        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subscriptionInfoList: List<SubscriptionInfo>? = subscriptionManager.activeSubscriptionInfoList

            subscriptionInfoList?.forEachIndexed { index, subscriptionInfo ->
                val mcc = subscriptionInfo.mccString ?: ""
                val mnc = subscriptionInfo.mncString ?: ""
                val carrierName = subscriptionInfo.carrierName?.toString() ?: "Unknown"
                
                simList.add(
                    SimInfo(
                        simSlot = index + 1,
                        carrierName = carrierName,
                        mcc = mcc,
                        mnc = mnc,
                        countryInfo = getCountryByMcc(mcc)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to single SIM info
            val mcc = getCurrentSimMcc() ?: ""
            val mnc = getCurrentSimMnc() ?: ""
            simList.add(
                SimInfo(
                    simSlot = 1,
                    carrierName = getCarrierName(),
                    mcc = mcc,
                    mnc = mnc,
                    countryInfo = getCountryByMcc(mcc)
                )
            )
        }
        return simList
    }

    /**
     * Check if SIM is from specific country
     */
    fun isSimFromCountry(countryIso: String): Boolean {
        return getCurrentSimCountry()?.iso?.equals(countryIso, ignoreCase = true) == true
    }

    /**
     * Get all available MCC data
     */
    fun getAllMccData(): List<CountryInfo> {
        return mccDataMap.values.toList()
    }

    /**
     * Search country by name
     */
    fun searchCountryByName(query: String): List<CountryInfo> {
        return mccDataMap.values.filter { 
            it.country.contains(query, ignoreCase = true) 
        }
    }

    /**
     * Get country by ISO code
     */
    fun getCountryByIso(iso: String): CountryInfo? {
        return mccDataMap.values.find { it.iso.equals(iso, ignoreCase = true) }
    }
}
