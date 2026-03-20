package com.message.bulksend.autorespond.ai.ui.customai

import org.json.JSONArray
import org.json.JSONObject

data class CustomBusinessFaqItem(
    val question: String = "",
    val answer: String = ""
)

data class CustomBusinessKnowledge(
    val businessName: String = "",
    val userName: String = "",
    val businessDetails: String = "",
    val faqs: List<CustomBusinessFaqItem> = emptyList()
) {
    fun hasContent(): Boolean {
        return businessName.isNotBlank() ||
            userName.isNotBlank() ||
            businessDetails.isNotBlank() ||
            faqs.any { it.question.isNotBlank() || it.answer.isNotBlank() }
    }
}

object CustomBusinessKnowledgeCodec {
    fun fromJson(raw: String): CustomBusinessKnowledge {
        if (raw.isBlank()) return CustomBusinessKnowledge()
        return try {
            val json = JSONObject(raw)
            val faqArray = json.optJSONArray("faqs") ?: JSONArray()
            val faqs = mutableListOf<CustomBusinessFaqItem>()
            for (index in 0 until faqArray.length()) {
                val item = faqArray.optJSONObject(index) ?: continue
                faqs +=
                    CustomBusinessFaqItem(
                        question = item.optString("question", ""),
                        answer = item.optString("answer", "")
                    )
            }
            CustomBusinessKnowledge(
                businessName = json.optString("businessName", ""),
                userName = json.optString("userName", ""),
                businessDetails = json.optString("businessDetails", ""),
                faqs = faqs
            )
        } catch (_error: Exception) {
            CustomBusinessKnowledge()
        }
    }

    fun toJson(value: CustomBusinessKnowledge): String {
        val faqArray = JSONArray()
        value.faqs.forEach { faq ->
            if (faq.question.isBlank() && faq.answer.isBlank()) return@forEach
            faqArray.put(
                JSONObject().apply {
                    put("question", faq.question.trim())
                    put("answer", faq.answer.trim())
                }
            )
        }
        return JSONObject()
            .apply {
                put("businessName", value.businessName.trim())
                put("userName", value.userName.trim())
                put("businessDetails", value.businessDetails.trim())
                put("faqs", faqArray)
            }
            .toString()
    }
}
