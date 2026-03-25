package com.message.bulksend.tablesheet.extractor

data class ExtractorResult(
    val emails: List<String>,
    val phoneNumbers: List<String>,
    val sourceLabel: String
)

data class ExtractorProgress(
    val fraction: Float,
    val message: String
)
