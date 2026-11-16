package com.easymomo.app.ussd

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Receives USSD prompt text from permitted Android mechanisms (Telephony UssdResponseCallback
 * or opt-in AccessibilityService), normalizes it, and emits to the UssdFlowController.
 */
object UssdPromptObserver {

    data class ObservedPrompt(
        val text: String,
        val source: PromptSource,
        val timestampMs: Long = System.currentTimeMillis()
    )

    enum class PromptSource {
        TELEPHONY_CALLBACK,
        ACCESSIBILITY_SERVICE,
        USER_ASSISTED
    }

    private val _prompts = MutableSharedFlow<ObservedPrompt>(extraBufferCapacity = 16)
    val prompts: SharedFlow<ObservedPrompt> = _prompts.asSharedFlow()

    fun onPromptObserved(rawText: String, source: PromptSource) {
        val cleaned = normalizeObservedText(rawText)
        if (cleaned.isNotBlank()) {
            _prompts.tryEmit(ObservedPrompt(cleaned, source))
        }
    }

    fun normalizeObservedText(raw: String): String {
        return raw
            .replace('\u00A0', ' ')
            .replace("\r\n", "\n")
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }
}
