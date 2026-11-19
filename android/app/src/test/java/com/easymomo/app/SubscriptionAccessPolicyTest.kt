package com.easymomo.app

import com.easymomo.app.domain.model.SubscriptionInfo
import com.easymomo.app.domain.model.SubscriptionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SubscriptionAccessPolicyTest {

    private val baseNow: Instant = Instant.parse("2026-11-19T12:00:00Z")
    private val baseNowMs: Long = baseNow.toEpochMilli()

    @Test
    fun `default free trial duration is 15 days`() {
        assertEquals(15, SubscriptionInfo.DEFAULT_TRIAL_DAYS)
    }

    @Test
    fun `NONE status denies access`() {
        val info = SubscriptionInfo(status = SubscriptionStatus.NONE)
        assertFalse(info.hasActiveAccess(baseNowMs))
        assertEquals(SubscriptionStatus.NONE, info.effectiveStatus(baseNowMs))
        assertEquals(0, info.effectiveRemainingDays(baseNowMs))
    }

    @Test
    fun `EXPIRED status denies access`() {
        val info = SubscriptionInfo(status = SubscriptionStatus.EXPIRED, remainingDays = 5)
        assertFalse(info.hasActiveAccess(baseNowMs))
        assertEquals(SubscriptionStatus.EXPIRED, info.effectiveStatus(baseNowMs))
        assertEquals(0, info.effectiveRemainingDays(baseNowMs))
    }

    @Test
    fun `CANCELLED status denies access`() {
        val info = SubscriptionInfo(status = SubscriptionStatus.fromCode("CANCELLED"))
        assertFalse(info.hasActiveAccess(baseNowMs))
        assertEquals(SubscriptionStatus.CANCELLED, info.effectiveStatus(baseNowMs))
        assertEquals(0, info.effectiveRemainingDays(baseNowMs))
    }

    @Test
    fun `active 15-day TRIAL with future trialExpiresAt grants access and reports 15 days`() {
        val trialEnd = baseNow.plusSeconds(15L * 24L * 60L * 60L)
        val info = SubscriptionInfo(
            status = SubscriptionStatus.TRIAL,
            trialStartedAt = baseNow.toString(),
            trialExpiresAt = trialEnd.toString(),
            remainingDays = 15
        )
        assertTrue(info.hasActiveAccess(baseNowMs))
        assertEquals(SubscriptionStatus.TRIAL, info.effectiveStatus(baseNowMs))
        assertEquals(15, info.effectiveRemainingDays(baseNowMs))
    }

    @Test
    fun `TRIAL with past trialExpiresAt transitions to EXPIRED and denies access locally`() {
        val pastEnd = baseNow.minusSeconds(60L)
        val info = SubscriptionInfo(
            status = SubscriptionStatus.TRIAL,
            trialStartedAt = baseNow.minusSeconds(15L * 24L * 60L * 60L).toString(),
            trialExpiresAt = pastEnd.toString(),
            remainingDays = 3
        )
        assertFalse(info.hasActiveAccess(baseNowMs))
        assertEquals(SubscriptionStatus.EXPIRED, info.effectiveStatus(baseNowMs))
        assertEquals(0, info.effectiveRemainingDays(baseNowMs))
    }

    @Test
    fun `ACTIVE subscription with future paidUntil grants access`() {
        val paidUntil = baseNow.plusSeconds(30L * 24L * 60L * 60L)
        val info = SubscriptionInfo(
            status = SubscriptionStatus.ACTIVE,
            paidUntil = paidUntil.toString(),
            currentPlanName = "Monthly Agent Plan",
            remainingDays = 30
        )
        assertTrue(info.hasActiveAccess(baseNowMs))
        assertEquals(SubscriptionStatus.ACTIVE, info.effectiveStatus(baseNowMs))
        assertEquals(30, info.effectiveRemainingDays(baseNowMs))
    }

    @Test
    fun `ACTIVE subscription with elapsed paidUntil transitions to EXPIRED and denies access`() {
        val elapsedPaidUntil = baseNow.minusSeconds(1L)
        val info = SubscriptionInfo(
            status = SubscriptionStatus.ACTIVE,
            paidUntil = elapsedPaidUntil.toString(),
            currentPlanName = "Monthly Agent Plan",
            remainingDays = 1
        )
        assertFalse(info.hasActiveAccess(baseNowMs))
        assertEquals(SubscriptionStatus.EXPIRED, info.effectiveStatus(baseNowMs))
        assertEquals(0, info.effectiveRemainingDays(baseNowMs))
    }

    @Test
    fun `TRIAL without timestamp requires positive remainingDays`() {
        val zeroDaysTrial = SubscriptionInfo(
            status = SubscriptionStatus.TRIAL,
            trialExpiresAt = null,
            remainingDays = 0
        )
        assertFalse(zeroDaysTrial.hasActiveAccess(baseNowMs))
        assertEquals(SubscriptionStatus.EXPIRED, zeroDaysTrial.effectiveStatus(baseNowMs))

        val fifteenDaysTrial = SubscriptionInfo(
            status = SubscriptionStatus.TRIAL,
            trialExpiresAt = null,
            remainingDays = 15
        )
        assertTrue(fifteenDaysTrial.hasActiveAccess(baseNowMs))
        assertEquals(SubscriptionStatus.TRIAL, fifteenDaysTrial.effectiveStatus(baseNowMs))
        assertEquals(15, fifteenDaysTrial.effectiveRemainingDays(baseNowMs))
    }
}
