package com.easymomo.app

import com.easymomo.app.domain.model.NetworkPrefixDetector
import com.easymomo.app.domain.model.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPrefixDetectorTest {

    @Test
    fun `detects MTN prefixes accurately`() {
        listOf("0244123456", "0251234567", "0541234567", "0559876543", "0591112222", "0530001111").forEach { num ->
            assertEquals(NetworkType.MTN, NetworkPrefixDetector.detectNetwork(num))
        }
        assertEquals(NetworkType.MTN, NetworkPrefixDetector.detectNetwork("+233251234567"))
    }

    @Test
    fun `detects AT Money prefixes accurately`() {
        listOf("0271234567", "0571234567", "0261234567", "0561234567").forEach { num ->
            assertEquals(NetworkType.AT, NetworkPrefixDetector.detectNetwork(num))
        }
    }

    @Test
    fun `detects Telecel prefixes accurately`() {
        listOf("0201234567", "0501234567").forEach { num ->
            assertEquals(NetworkType.TELECEL, NetworkPrefixDetector.detectNetwork(num))
        }
    }

    @Test
    fun `normalizes +233 and spaces`() {
        assertEquals("0244123456", NetworkPrefixDetector.normalizeGhanaPhone("+233 24 412 3456"))
        assertEquals(NetworkType.MTN, NetworkPrefixDetector.detectNetwork("+233244123456"))
    }

    @Test
    fun `respects ported number override`() {
        val detected = NetworkPrefixDetector.detectNetwork(
            rawPhone = "0244123456",
            isPorted = true,
            manualOverride = NetworkType.TELECEL
        )
        assertEquals(NetworkType.TELECEL, detected)
    }

    @Test
    fun `validates Ghana phone length and prefix`() {
        assertTrue(NetworkPrefixDetector.isValidGhanaPhone("0244123456"))
        assertFalse(NetworkPrefixDetector.isValidGhanaPhone("02441234"))
        assertFalse(NetworkPrefixDetector.isValidGhanaPhone("0991234567"))
    }
}
