package com.easymomo.app

import com.easymomo.app.ussd.DualSimAccountCandidate
import com.easymomo.app.ussd.DualSimSubscriptionDescriptor
import com.easymomo.app.ussd.matchDualSimAccountIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DualSimHandleMatcherTest {

    @Test
    fun `matches official API 30 subscriptionId first`() {
        val subs = listOf(
            DualSimSubscriptionDescriptor(subscriptionId = 1, slotIndex = 0, carrierName = "MTN GH"),
            DualSimSubscriptionDescriptor(subscriptionId = 2, slotIndex = 1, carrierName = "MTN GH")
        )
        val candidates = listOf(
            DualSimAccountCandidate(id = "icc-2", subscriptionIdFromApi = 2),
            DualSimAccountCandidate(id = "icc-1", subscriptionIdFromApi = 1)
        )

        assertEquals(1, matchDualSimAccountIndex(1, subs, candidates))
        assertEquals(0, matchDualSimAccountIndex(2, subs, candidates))
    }

    @Test
    fun `matches blank-ICCID OEM dual SIM (e-g Tecno Infinix MediaTek) to SIM 1 and SIM 2`() {
        // Common Android 10-14 OEM case: SubscriptionInfo.iccId is blank, Telecom handle.id is raw ICCID
        // and both SIMs share the exact same carrier name "MTN GH".
        val subs = listOf(
            DualSimSubscriptionDescriptor(
                subscriptionId = 3,
                slotIndex = 0,
                iccId = "",
                displayName = "MTN GH",
                carrierName = "MTN GH"
            ),
            DualSimSubscriptionDescriptor(
                subscriptionId = 4,
                slotIndex = 1,
                iccId = "",
                displayName = "MTN GH",
                carrierName = "MTN GH"
            )
        )
        val candidates = listOf(
            DualSimAccountCandidate(id = "892330100000000111F", label = "MTN GH"),
            DualSimAccountCandidate(id = "892330100000000222F", label = "MTN GH")
        )

        assertEquals(0, matchDualSimAccountIndex(3, subs, candidates))
        assertEquals(1, matchDualSimAccountIndex(4, subs, candidates))
    }

    @Test
    fun `matches via EXTRA_SORT_ORDER even when Telecom handles are returned out of order`() {
        val subs = listOf(
            DualSimSubscriptionDescriptor(subscriptionId = 10, slotIndex = 0, carrierName = "MTN GH"),
            DualSimSubscriptionDescriptor(subscriptionId = 11, slotIndex = 1, carrierName = "MTN GH")
        )
        val candidates = listOf(
            DualSimAccountCandidate(id = "non-sim-voip", isSimAccount = false),
            DualSimAccountCandidate(id = "opaque-b", sortOrder = 1, label = "MTN GH"),
            DualSimAccountCandidate(id = "opaque-a", sortOrder = 0, label = "MTN GH")
        )

        assertEquals(2, matchDualSimAccountIndex(10, subs, candidates))
        assertEquals(1, matchDualSimAccountIndex(11, subs, candidates))
    }

    @Test
    fun `matches ICCID even with trailing F padding or punctuation differences`() {
        val subs = listOf(
            DualSimSubscriptionDescriptor(subscriptionId = 1, slotIndex = 0, iccId = "8923301-123456789"),
            DualSimSubscriptionDescriptor(subscriptionId = 2, slotIndex = 1, iccId = "8923301-987654321")
        )
        val candidates = listOf(
            DualSimAccountCandidate(id = "8923301987654321F"),
            DualSimAccountCandidate(id = "8923301123456789f")
        )

        assertEquals(1, matchDualSimAccountIndex(1, subs, candidates))
        assertEquals(0, matchDualSimAccountIndex(2, subs, candidates))
    }

    @Test
    fun `identical carrier names on dual SIM do not collide and route accurately to SIM 2`() {
        val subs = listOf(
            DualSimSubscriptionDescriptor(
                subscriptionId = 5,
                slotIndex = 0,
                displayName = "MTN GH",
                carrierName = "MTN GH"
            ),
            DualSimSubscriptionDescriptor(
                subscriptionId = 6,
                slotIndex = 1,
                displayName = "MTN GH",
                carrierName = "MTN GH"
            )
        )
        val candidates = listOf(
            DualSimAccountCandidate(id = "h0", label = "MTN GH", shortDescription = "MTN GH"),
            DualSimAccountCandidate(id = "h1", label = "MTN GH", shortDescription = "MTN GH")
        )

        assertEquals(1, matchDualSimAccountIndex(6, subs, candidates))
        assertEquals(0, matchDualSimAccountIndex(5, subs, candidates))
    }

    @Test
    fun `returns null when no candidates exist`() {
        val subs = listOf(DualSimSubscriptionDescriptor(subscriptionId = 1, slotIndex = 0))
        assertNull(matchDualSimAccountIndex(1, subs, emptyList()))
    }
}
