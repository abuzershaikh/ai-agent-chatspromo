package com.message.bulksend.tablesheet.data.models

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class CurrencyInfo(
    val country: String,
    val code: String,
    val symbol: String
)

object CurrencyHelper {
    private var currencies: List<CurrencyInfo>? = null
    
    fun loadCurrencies(context: Context): List<CurrencyInfo> {
        if (currencies != null) return currencies!!
        
        val list = mutableListOf<CurrencyInfo>()
        try {
            val inputStream = context.assets.open("Currency.csv")
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            // Skip header
            reader.readLine()
            
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { csvLine ->
                    val parts = csvLine.split(",").map { it.trim().removeSurrounding("\"") }
                    if (parts.size >= 3) {
                        val country = parts[0]
                        val code = parts[1]
                        val unicodeHex = parts[2]
                        
                        // Convert unicode hex to actual symbol
                        val symbol = parseUnicodeString(unicodeHex)
                        
                        list.add(CurrencyInfo(country, code, symbol))
                    }
                }
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
            // Add some defaults if file fails
            list.add(CurrencyInfo("India Rupee", "INR", "₹"))
            list.add(CurrencyInfo("United States Dollar", "USD", "$"))
            list.add(CurrencyInfo("Euro", "EUR", "€"))
        }
        
        currencies = list
        return list
    }
    
    private fun parseUnicodeString(unicodeHex: String): String {
        val sb = StringBuilder()
        val pattern = Regex("\\\\u([0-9a-fA-F]{4})")
        var lastEnd = 0
        
        pattern.findAll(unicodeHex).forEach { match ->
            // Add any text before this match
            if (match.range.first > lastEnd) {
                sb.append(unicodeHex.substring(lastEnd, match.range.first))
            }
            // Convert hex to char
            val hex = match.groupValues[1]
            val codePoint = hex.toInt(16)
            sb.append(codePoint.toChar())
            lastEnd = match.range.last + 1
        }
        
        // Add remaining text
        if (lastEnd < unicodeHex.length) {
            sb.append(unicodeHex.substring(lastEnd))
        }
        
        return if (sb.isEmpty()) unicodeHex else sb.toString()
    }
    
    fun getCurrencyByCode(context: Context, code: String): CurrencyInfo? {
        return loadCurrencies(context).find { it.code == code }
    }
    
    // Parse selectOptions format: "INR|₹|left" or "USD|$|right"
    fun parseCurrencyOptions(options: String?): Triple<String, String, String>? {
        if (options.isNullOrBlank()) return null
        val parts = options.split("|")
        if (parts.size >= 3) {
            return Triple(parts[0], parts[1], parts[2])
        }
        return null
    }
    
    fun formatCurrencyOptions(code: String, symbol: String, position: String): String {
        return "$code|$symbol|$position"
    }
}
