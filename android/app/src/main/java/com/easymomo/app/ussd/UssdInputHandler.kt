package com.easymomo.app.ussd

import com.easymomo.app.domain.model.NetworkPrefixDetector
import com.easymomo.app.domain.model.NetworkType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Validates and formats dynamic transaction variables before USSD substitution.
 * Prevents empty values, wrong-network numbers, and USSD command injection (*, #).
 */
class UssdInputHandler {

    data class TransactionVariables(
        val customerPhone: String = "",
        val recipientPhone: String = "",
        val amount: String = "",
        val bundleCode: String = "",
        val bundlePrice: String = "",
        val network: NetworkType = NetworkType.UNKNOWN,
        val merchantCode: String = "",
        val customUssdCode: String = "",
        val reference: String = "",
        val operationalId: String = "",
        val isPortedNumber: Boolean = false
    )

    sealed interface ValidationResult {
        data class Valid(val value: String) : ValidationResult
        data class Invalid(val reason: String) : ValidationResult
    }

    fun formatPhoneNumber(raw: String): ValidationResult {
        if (raw.isBlank()) return ValidationResult.Invalid("Phone number is required")
        if (containsUssdControlChars(raw)) {
            return ValidationResult.Invalid("Phone number contains invalid characters")
        }
        val normalized = NetworkPrefixDetector.normalizeGhanaPhone(raw)
        return if (NetworkPrefixDetector.isValidGhanaPhone(normalized)) {
            ValidationResult.Valid(normalized)
        } else {
            ValidationResult.Invalid("Enter a valid 10-digit Ghana mobile number (e.g. 0244123456)")
        }
    }

    fun validatePhoneForNetwork(
        rawPhone: String,
        expectedNetwork: NetworkType,
        isPorted: Boolean
    ): ValidationResult {
        val phoneResult = formatPhoneNumber(rawPhone)
        if (phoneResult is ValidationResult.Invalid) return phoneResult
        val normalized = (phoneResult as ValidationResult.Valid).value

        if (!isPorted && expectedNetwork != NetworkType.UNKNOWN) {
            val detected = NetworkPrefixDetector.detectNetwork(normalized, isPorted = false)
            if (detected != NetworkType.UNKNOWN && detected != expectedNetwork) {
                return ValidationResult.Invalid(
                    "Number $normalized belongs to ${detected.displayName}, not ${expectedNetwork.displayName}. Check 'Number is ported' if applicable."
                )
            }
        }
        return ValidationResult.Valid(normalized)
    }

    fun formatAmount(raw: String): ValidationResult {
        if (raw.isBlank()) return ValidationResult.Invalid("Amount is required")
        if (containsUssdControlChars(raw)) {
            return ValidationResult.Invalid("Amount contains invalid characters")
        }
        val cleaned = raw.trim().replace(",", "")
        val decimal = cleaned.toBigDecimalOrNull()
            ?: return ValidationResult.Invalid("Enter a valid numeric amount")
        if (decimal <= BigDecimal.ZERO) {
            return ValidationResult.Invalid("Amount must be greater than 0")
        }
        if (decimal > BigDecimal("50000")) {
            return ValidationResult.Invalid("Amount exceeds single-transaction safety limit")
        }
        val formatted = if (decimal.stripTrailingZeros().scale() <= 0) {
            decimal.setScale(0, RoundingMode.UNNECESSARY).toPlainString()
        } else {
            decimal.setScale(2, RoundingMode.HALF_UP).toPlainString()
        }
        return ValidationResult.Valid(formatted)
    }

    fun resolveVariable(variableName: String, vars: TransactionVariables): ValidationResult {
        return when (variableName) {
            "customer_phone" -> validatePhoneForNetwork(vars.customerPhone, vars.network, vars.isPortedNumber)
            "recipient_phone" -> validatePhoneForNetwork(
                vars.recipientPhone.ifBlank { vars.customerPhone },
                vars.network,
                vars.isPortedNumber
            )
            "amount" -> formatAmount(vars.amount)
            "bundle_code" -> sanitizeSafeAlphanumeric(vars.bundleCode, "Bundle code")
            "bundle_price" -> formatAmount(vars.bundlePrice.ifBlank { vars.amount })
            "network" -> ValidationResult.Valid(vars.network.code)
            "merchant_code" -> sanitizeSafeAlphanumeric(vars.merchantCode, "Merchant code")
            "reference" -> resolveReference(vars.reference)
            "operational_id" -> sanitizeSafeAlphanumeric(vars.operationalId, "Telecel operational ID")
            else -> ValidationResult.Invalid("Unknown USSD variable: $variableName")
        }
    }

    fun resolveReference(rawReference: String): ValidationResult {
        val trimmed = rawReference.trim()
        if (trimmed.isEmpty()) {
            // Generate a non-sensitive alphanumeric reference (never "1")
            val suffix = (System.currentTimeMillis() % 1_000_000L).toString().padStart(6, '0')
            return ValidationResult.Valid("AMA$suffix")
        }
        if (containsUssdControlChars(trimmed) || !trimmed.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            return ValidationResult.Invalid("Reference contains invalid characters")
        }
        return ValidationResult.Valid(trimmed.take(20))
    }

    private fun sanitizeSafeAlphanumeric(raw: String, fieldName: String): ValidationResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ValidationResult.Invalid("$fieldName is required")
        if (containsUssdControlChars(trimmed) || !trimmed.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            return ValidationResult.Invalid("$fieldName contains invalid characters")
        }
        return ValidationResult.Valid(trimmed)
    }

    private fun containsUssdControlChars(input: String): Boolean {
        return input.any { it == '*' || it == '#' || it == ';' || it == '\n' || it == '\r' }
    }
}
