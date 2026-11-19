package com.easymomo.app

import com.easymomo.app.ussd.UssdPromptMatcher
import com.easymomo.app.ussd.model.PromptMatchMode
import com.easymomo.app.ussd.model.StepAction
import com.easymomo.app.ussd.model.StepType
import com.easymomo.app.ussd.model.UssdFlowStep
import org.junit.Assert.assertTrue
import org.junit.Test

class UssdPromptMatcherTest {

    private val matcher = UssdPromptMatcher()

    @Test
    fun `matches normalized text ignoring whitespace and case`() {
        val step = UssdFlowStep(
            id = "ENTER_NUM",
            type = StepType.MATCH_PROMPT,
            expectedPrompts = listOf("Enter customer mobile number"),
            matchMode = PromptMatchMode.NORMALIZED_TEXT,
            action = StepAction.SEND_VARIABLE
        )
        val result = matcher.matchStep("  ENTER   Customer\nMobile   Number: ", step)
        assertTrue(result is UssdPromptMatcher.MatchResult.Matched)
    }

    @Test
    fun `matches regex variants`() {
        val step = UssdFlowStep(
            id = "ENTER_AMT",
            type = StepType.MATCH_PROMPT,
            expectedPrompts = listOf("""enter\s+amount\s*\(ghs\)"""),
            matchMode = PromptMatchMode.REGEX,
            action = StepAction.SEND_VARIABLE
        )
        val result = matcher.matchStep("Please Enter Amount (GHS):", step)
        assertTrue(result is UssdPromptMatcher.MatchResult.Matched)
    }

    @Test
    fun `detects unexpected menu prompt`() {
        val step = UssdFlowStep(
            id = "MAIN",
            type = StepType.MATCH_PROMPT,
            expectedPrompts = listOf("1. Cash In"),
            matchMode = PromptMatchMode.NORMALIZED_TEXT,
            action = StepAction.SEND_MENU_OPTION
        )
        val result = matcher.matchStep("1. Pay Utility\n2. Loans", step)
        assertTrue(result is UssdPromptMatcher.MatchResult.UnexpectedPrompt)
    }

    @Test
    fun `detects provider error`() {
        val step = UssdFlowStep(
            id = "MAIN",
            type = StepType.MATCH_PROMPT,
            expectedPrompts = listOf("1. Cash In")
        )
        val result = matcher.matchStep("Connection problem or invalid MMI code.", step)
        assertTrue(result is UssdPromptMatcher.MatchResult.ProviderError)
    }

    @Test
    fun `does not false match numeric menu prefixes or short tokens inside other words`() {
        val menuStep = UssdFlowStep(
            id = "MENU",
            type = StepType.MATCH_PROMPT,
            expectedPrompts = listOf("1.", "2."),
            matchMode = PromptMatchMode.NORMALIZED_TEXT,
            action = StepAction.SEND_MENU_OPTION
        )
        assertTrue(matcher.matchStep("Enter amount (min 1.00, max 2.50):", menuStep) is UssdPromptMatcher.MatchResult.UnexpectedPrompt)
        assertTrue(matcher.matchStep("1. Specify Amount", menuStep) is UssdPromptMatcher.MatchResult.Matched)

        val idStep = UssdFlowStep(
            id = "MERCHANT",
            type = StepType.MATCH_PROMPT,
            expectedPrompts = listOf("id"),
            matchMode = PromptMatchMode.NORMALIZED_TEXT,
            action = StepAction.SEND_VARIABLE
        )
        assertTrue(matcher.matchStep("Paid successfully", idStep) is UssdPromptMatcher.MatchResult.UnexpectedPrompt)
        assertTrue(matcher.matchStep("Enter Merchant ID:", idStep) is UssdPromptMatcher.MatchResult.Matched)

        val refStep = UssdFlowStep(
            id = "REF",
            type = StepType.MATCH_PROMPT,
            expectedPrompts = listOf("ref"),
            matchMode = PromptMatchMode.NORMALIZED_TEXT,
            action = StepAction.PAUSE_FOR_REFERENCE
        )
        assertTrue(matcher.matchStep("Refund policy", refStep) is UssdPromptMatcher.MatchResult.UnexpectedPrompt)
        assertTrue(matcher.matchStep("Enter Ref:", refStep) is UssdPromptMatcher.MatchResult.Matched)
    }

    @Test
    fun `termination and new provider error indicators win before expectedPrompts`() {
        val momoMenuStep = UssdFlowStep(
            id = "MAIN_MENU",
            type = StepType.MATCH_PROMPT,
            expectedPrompts = listOf("momo", "transfer money"),
            matchMode = PromptMatchMode.NORMALIZED_TEXT,
            action = StepAction.SEND_MENU_OPTION
        )
        assertTrue(matcher.matchStep("Thank you for using MoMo.", momoMenuStep) is UssdPromptMatcher.MatchResult.SessionTerminated)
        assertTrue(matcher.matchStep("MoMo session expired.", momoMenuStep) is UssdPromptMatcher.MatchResult.SessionTerminated)
        assertTrue(matcher.matchStep("Incorrect PIN entered.", momoMenuStep) is UssdPromptMatcher.MatchResult.ProviderError)
        assertTrue(matcher.matchStep("Invalid amount specified.", momoMenuStep) is UssdPromptMatcher.MatchResult.ProviderError)
        assertTrue(matcher.matchStep("Invalid operational ID.", momoMenuStep) is UssdPromptMatcher.MatchResult.ProviderError)
    }
}
