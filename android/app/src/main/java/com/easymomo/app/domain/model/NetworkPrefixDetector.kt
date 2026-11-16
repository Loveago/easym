package com.easymomo.app.domain.model

object NetworkPrefixDetector {

    private val mtnPrefixes = setOf("024", "054", "055", "059", "053")
    private val atPrefixes = setOf("027", "057", "026", "056")
    private val telecelPrefixes = setOf("020", "050")

    /**
     * Normalizes a Ghana phone number to local 10-digit format (0XXXXXXXXX) if possible.
     * Strips spaces, hyphens, parentheses, and +233 / 233 country prefix.
     */
    fun normalizeGhanaPhone(raw: String): String {
        val digitsOnly = raw.filter { it.isDigit() }
        return when {
            digitsOnly.startsWith("233") && digitsOnly.length == 12 -> "0" + digitsOnly.substring(3)
            digitsOnly.startsWith("0") && digitsOnly.length == 10 -> digitsOnly
            digitsOnly.length == 9 && !digitsOnly.startsWith("0") -> "0$digitsOnly"
            else -> digitsOnly
        }
    }

    /**
     * Normalizes partial or full search queries (including +233 / 233 prefixes)
     * so prefix and partial-digit autocomplete matches local 0XXXXXXXXX numbers.
     */
    fun normalizeSearchQuery(rawQuery: String): String {
        val digitsOnly = rawQuery.filter { it.isDigit() }
        return when {
            digitsOnly.startsWith("233") && digitsOnly.length > 3 -> "0" + digitsOnly.substring(3)
            digitsOnly.length == 9 && !digitsOnly.startsWith("0") -> "0$digitsOnly"
            else -> digitsOnly
        }
    }

    /**
     * Validates whether the input represents a valid 10-digit Ghana mobile number (0[2|5]XXXXXXXX).
     */
    fun isValidGhanaPhone(raw: String): Boolean {
        val normalized = normalizeGhanaPhone(raw)
        return normalized.length == 10 &&
            normalized.startsWith("0") &&
            (normalized[1] == '2' || normalized[1] == '5')
    }

    /**
     * Detects the mobile network from a Ghana phone number's 3-digit prefix.
     * If [isPorted] is true and [manualOverride] is provided (not UNKNOWN), returns [manualOverride].
     */
    fun detectNetwork(
        rawPhone: String,
        isPorted: Boolean = false,
        manualOverride: NetworkType = NetworkType.UNKNOWN
    ): NetworkType {
        if (isPorted && manualOverride != NetworkType.UNKNOWN) {
            return manualOverride
        }
        val normalized = normalizeGhanaPhone(rawPhone)
        if (normalized.length < 3) return NetworkType.UNKNOWN
        val prefix = normalized.substring(0, 3)
        return when {
            prefix in mtnPrefixes -> NetworkType.MTN
            prefix in atPrefixes -> NetworkType.AT
            prefix in telecelPrefixes -> NetworkType.TELECEL
            else -> NetworkType.UNKNOWN
        }
    }
}
