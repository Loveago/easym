package com.easymomo.app.ussd

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticLogEntry(
    val timestamp: String,
    val stage: String,
    val message: String
)

/**
 * In-memory sanitized diagnostic log buffer for USSD debugging.
 * Never logs PINs, OTPs, or full sensitive USSD screen contents.
 */
object UssdDiagnosticLog {
    private const val MAX_ENTRIES = 60
    private val entries = ArrayDeque<DiagnosticLogEntry>(MAX_ENTRIES)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun log(stage: String, message: String) {
        val sanitized = sanitize(message)
        if (entries.size >= MAX_ENTRIES) {
            entries.removeFirst()
        }
        entries.addLast(DiagnosticLogEntry(timeFormat.format(Date()), stage, sanitized))
    }

    @Synchronized
    fun getEntries(): List<DiagnosticLogEntry> = entries.toList().asReversed()

    @Synchronized
    fun clear() = entries.clear()

    private fun sanitize(input: String): String {
        val lower = input.lowercase()
        if (lower.contains("pin") || lower.contains("otp") || lower.contains("secret")) {
            return "[Sensitive authorization prompt redacted]"
        }
        return input.replace(Regex("\\b\\d{4,6}\\b"), "****").take(180)
    }
}
