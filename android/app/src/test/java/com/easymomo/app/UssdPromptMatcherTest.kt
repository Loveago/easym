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
}
