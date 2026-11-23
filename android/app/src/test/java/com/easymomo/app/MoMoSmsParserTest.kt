package com.easymomo.app

import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.TransactionType
import com.easymomo.app.ussd.sms.MoMoSmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MoMoSmsParserTest {

    @Test
    fun `parses MTN MoMo Cash In SMS`() {
        val sms = "Cash In of GHS 150.00 to KWAME MENSAH (0244123456). Financial Transaction Id: 98765432101. Fee: GHS 0.00. Current Balance: GHS 1,200.00."
        val parsed = MoMoSmsParser.parse("MobileMoney", sms)
        assertNotNull(parsed)
        assertEquals(NetworkType.MTN, parsed!!.network)
        assertEquals(TransactionType.CASH_IN, parsed.transactionType)
        assertEquals(150.0, parsed.amount, 0.001)
        assertEquals("KWAME MENSAH", parsed.customerName)
        assertEquals("0244123456", parsed.phoneNumber)
        assertEquals("98765432101", parsed.providerReference)
    }

    @Test
    fun `parses customer name even without phone number`() {
        val sms = "Cash Out of GHS 100.00 from EMMANUEL AGO. Financial Transaction Id: 5566778899. Current Balance: GHS 500.00."
        val parsed = MoMoSmsParser.parse("MobileMoney", sms)
        assertNotNull(parsed)
        assertEquals(NetworkType.MTN, parsed!!.network)
        assertEquals(TransactionType.CASH_OUT, parsed.transactionType)
        assertEquals(100.0, parsed.amount, 0.001)
        assertEquals("EMMANUEL AGO", parsed.customerName)
        assertEquals("", parsed.phoneNumber)
        assertEquals("5566778899", parsed.providerReference)
    }

    @Test
    fun `parses Telecel Cash Confirmation SMS`() {
        val sms = "00000123456789 Confirmed. GHS 200.00 Cash Out from 0209876543. Fee: GHS 1.50. Available Balance: GHS 800.00."
        val parsed = MoMoSmsParser.parse("TelecelCash", sms)
        assertNotNull(parsed)
        assertEquals(NetworkType.TELECEL, parsed!!.network)
        assertEquals(TransactionType.CASH_OUT, parsed.transactionType)
        assertEquals(200.0, parsed.amount, 0.001)
        assertEquals(1.5, parsed.fee, 0.001)
        assertEquals("0209876543", parsed.phoneNumber)
        assertEquals("00000123456789", parsed.providerReference)
    }

    @Test
    fun `ignores OTP and non-transactional messages`() {
        val otp = "Your OTP is 482910. Do not share this code with anyone."
        assertNull(MoMoSmsParser.parse("MobileMoney", otp))
        assertNull(MoMoSmsParser.parse("John Doe", "Hey, are you at the shop?"))
    }
}
