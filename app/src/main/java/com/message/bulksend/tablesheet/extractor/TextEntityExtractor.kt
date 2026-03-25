package com.message.bulksend.tablesheet.extractor

import java.util.LinkedHashSet
import java.util.Locale

object TextEntityExtractor {
    private val strictEmailRegex =
        Regex(
            pattern = "^[A-Z0-9._%+-]{1,64}@[A-Z0-9-]+(?:\\.[A-Z0-9-]+)+$",
            option = RegexOption.IGNORE_CASE
        )

    // OCR-friendly email candidate:
    // supports spaces around '@' and '.' like: "sales @ company . com"
    private val emailCandidateRegex =
        Regex(
            pattern = "(?<![A-Z0-9._%+-])([A-Z0-9._%+-]{1,64})\\s*@\\s*([A-Z0-9-]+(?:\\s*\\.\\s*[A-Z0-9-]+)+)",
            option = RegexOption.IGNORE_CASE
        )

    // Global phone candidate extraction with OCR confusion tolerance.
    private val globalPhoneCandidateRegex =
        Regex(
            pattern = "(?<![A-Za-z0-9])(?:\\+|00)?[0-9OoIlSsBbZzGgQqDd][0-9OoIlSsBbZzGgQqDd\\s().\\-]{3,}[0-9OoIlSsBbZzGgQqDd](?![A-Za-z0-9])",
            option = RegexOption.IGNORE_CASE
        )

    fun extract(rawText: String): Pair<List<String>, List<String>> {
        val phoneText = normalizeOcrPhoneText(rawText)
        val emailText = normalizeOcrEmailText(rawText)

        val emails = LinkedHashSet<String>()
        emailCandidateRegex.findAll(emailText).forEach { match ->
            val local = match.groupValues.getOrNull(1).orEmpty()
            val domain = match.groupValues.getOrNull(2).orEmpty()
            val combined = "$local@$domain"
            normalizeEmail(combined)?.let { normalized ->
                emails += normalized
            }
        }

        val phones = LinkedHashSet<String>()
        globalPhoneCandidateRegex.findAll(phoneText).forEach { match ->
            normalizePhone(match.value)?.let { normalized ->
                phones += normalized
            }
        }

        return emails.toList() to phones.toList()
    }

    private fun normalizeOcrPhoneText(raw: String): String {
        if (raw.isBlank()) return raw
        val out = StringBuilder(raw.length)
        raw.forEach { ch ->
            out.append(
                when (ch) {
                    // OCR confusions commonly seen in video/image text.
                    'O', 'o', 'D', 'd', 'Q', 'q' -> '0'
                    'I', 'l', '|' -> '1'
                    'S', 's', '$' -> '5'
                    'B', 'b' -> '8'
                    'Z', 'z' -> '2'
                    'G', 'g' -> '6'
                    else -> ch
                }
            )
        }
        return out.toString()
    }

    private fun normalizeOcrEmailText(raw: String): String {
        if (raw.isBlank()) return raw
        return raw
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')
            .replace(Regex("\\s+"), " ")
    }

    private fun normalizeEmail(raw: String): String? {
        val compact =
            raw.trim()
                .replace(Regex("\\s*@\\s*"), "@")
                .replace(Regex("\\s*\\.\\s*"), ".")
                .trim { it in " \t\r\n,;:!?)[]{}<>\"'" }
                .lowercase(Locale.ROOT)

        if (!strictEmailRegex.matches(compact)) return null

        val tld = compact.substringAfterLast('.', "")
        if (tld.length < 2) return null
        return compact
    }

    private fun normalizePhone(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        val hasPlusPrefix = trimmed.startsWith("+")
        val hasDoubleZeroPrefix = trimmed.startsWith("00")
        var digits = trimmed.filter(Char::isDigit)

        if (digits.startsWith("00") && digits.length > 2) {
            digits = digits.drop(2)
        }

        // Reject invalid country-code form like +0....
        if ((hasPlusPrefix || hasDoubleZeroPrefix) && digits.startsWith("0")) return null

        // Keep minimum/maximum for practical extraction use.
        if (digits.length !in 5..15) return null

        // Guard against noisy sequences like 00000000 or 111111111.
        if (digits.toSet().size <= 1) return null

        return when {
            hasPlusPrefix || hasDoubleZeroPrefix || digits.length > 10 -> "+$digits"
            else -> digits
        }
    }
}
