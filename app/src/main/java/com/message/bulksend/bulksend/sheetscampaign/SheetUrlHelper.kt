package com.message.bulksend.bulksend.sheetscampaign

/**
 * Helper class for Sheet URL functionality
 */
object SheetUrlHelper {
    
    /**
     * Convert Google Sheets sharing URL to CSV export URL
     */
    fun convertGoogleSheetsUrl(shareUrl: String): String {
        return when {
            shareUrl.contains("docs.google.com/spreadsheets") && shareUrl.contains("/edit") -> {
                val sheetId = extractGoogleSheetId(shareUrl)
                if (sheetId != null) {
                    "https://docs.google.com/spreadsheets/d/$sheetId/export?format=csv"
                } else {
                    shareUrl
                }
            }
            else -> shareUrl
        }
    }
    
    /**
     * Extract Google Sheet ID from sharing URL
     */
    private fun extractGoogleSheetId(url: String): String? {
        return try {
            val regex = "/spreadsheets/d/([a-zA-Z0-9-_]+)".toRegex()
            regex.find(url)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Validate if URL is a supported format
     */
    fun isSupportedUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }
    
    /**
     * Get example URLs for testing
     */
    fun getExampleUrls(): List<Pair<String, String>> {
        return listOf(
            "Google Sheets" to "https://docs.google.com/spreadsheets/d/YOUR_SHEET_ID/edit#gid=0",
            "CSV File" to "https://example.com/data.csv",
            "Excel File" to "https://example.com/data.xlsx"
        )
    }
    
    /**
     * Get usage instructions
     */
    fun getUsageInstructions(): List<String> {
        return listOf(
            "1. For Google Sheets: Share your sheet and copy the link",
            "2. Make sure the sheet is publicly accessible or shared with 'Anyone with the link'",
            "3. Paste the URL in the 'Sheet URL' field",
            "4. Click the refresh icon to load the data",
            "5. The sheet will be automatically parsed and displayed",
            "6. Use the refresh icon anytime to reload the latest data"
        )
    }
}