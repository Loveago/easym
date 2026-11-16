package com.easymomo.app

import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.ussd.UssdInputHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UssdInputHandlerTest {

    private val handler = UssdInputHandler()

    @Test
    fun `rejects USSD injection characters`() {
        val res = handler.formatPhoneNumber("0244123456*1#")
        assertTrue(res is UssdInputHandler.ValidationResult.Invalid)
    }

    @Test
    fun `rejects wrong network when not ported`() {
        val res = handler.validatePhoneForNetwork("0201234567", NetworkType.MTN, isPorted = false)
        assertTrue(res is UssdInputHandler.ValidationResult.Invalid)
    }

    @Test
    fun `allows wrong prefix when ported is checked`() {
        val res = handler.validatePhoneForNetwork("0201234567", NetworkType.MTN, isPorted = true)
        assertTrue(res is UssdInputHandler.ValidationResult.Valid)
        assertEquals("0201234567", (res as UssdInputHandler.ValidationResult.Valid).value)
    }

    @Test
    fun `formats monetary amounts cleanly`() {
        val whole = handler.formatAmount("100.00")
        assertEquals("100", (whole as UssdInputHandler.ValidationResult.Valid).value)

        val decimal = handler.formatAmount("25.5")
        assertEquals("25.50", (decimal as UssdInputHandler.ValidationResult.Valid).value)

        assertTrue(handler.formatAmount("0") is UssdInputHandler.ValidationResult.Invalid)
        assertTrue(handler.formatAmount("-10") is UssdInputHandler.ValidationResult.Invalid)
    }
}
