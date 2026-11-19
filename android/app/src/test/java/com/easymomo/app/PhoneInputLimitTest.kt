package com.easymomo.app

import com.easymomo.app.ui.terminal.TerminalViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneInputLimitTest {

    @Test
    fun `allows exact 10-digit local phone number`() {
        assertEquals("0244123456", TerminalViewModel.sanitizeDashboardPhoneInput("0244123456"))
    }

    @Test
    fun `truncates pasted or typed numbers beyond 10 digits`() {
        assertEquals("0244123456", TerminalViewModel.sanitizeDashboardPhoneInput("0244123456789"))
    }

    @Test
    fun `strips non-digit formatting characters and keeps 10 digits`() {
        assertEquals("0244123456", TerminalViewModel.sanitizeDashboardPhoneInput("024-412-3456"))
        assertEquals("0244123456", TerminalViewModel.sanitizeDashboardPhoneInput("024 412 3456"))
    }

    @Test
    fun `normalizes +233 country prefix to 10-digit local format`() {
        assertEquals("0244123456", TerminalViewModel.sanitizeDashboardPhoneInput("+233244123456"))
        assertEquals("0244123456", TerminalViewModel.sanitizeDashboardPhoneInput(" +233 24 412 3456 "))
    }

    @Test
    fun `normalizes 233 country prefix to 10-digit local format`() {
        assertEquals("0599555182", TerminalViewModel.sanitizeDashboardPhoneInput("233599555182"))
    }

    @Test
    fun `preserves partial input under 10 digits`() {
        assertEquals("024", TerminalViewModel.sanitizeDashboardPhoneInput("024"))
        assertEquals("", TerminalViewModel.sanitizeDashboardPhoneInput(""))
    }
}
