package com.easymomo.app.ussd.model

import com.google.gson.annotations.SerializedName

enum class PromptMatchMode {
    EXACT,
    NORMALIZED_TEXT,
    REGEX
}

enum class StepType {
    INITIATE_USSD,
    MATCH_PROMPT,
    REQUIRE_AUTHORIZATION,
    MATCH_RESULT
}

enum class StepAction {
    SEND_MENU_OPTION,
    SEND_VARIABLE,
    REQUIRE_CONFIRMATION,
    PAUSE_FOR_OPERATIONAL_ID,
    PAUSE_FOR_PIN,
    WAIT_FOR_PROMPT,
    COMPLETE
}

enum class ResponseType {
    STATIC_OPTION,
    TRANSACTION_CUSTOMER_PHONE,
    TRANSACTION_AMOUNT,
    MANUAL_OPERATIONAL_ID,
    MANUAL_SENSITIVE_INPUT,
    WAIT_FOR_PROMPT,
    TERMINAL_RESULT
}

data class UssdFlowStep(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: StepType,
    @SerializedName("expected_prompts") val expectedPrompts: List<String> = emptyList(),
    @SerializedName("match_mode") val matchMode: PromptMatchMode = PromptMatchMode.NORMALIZED_TEXT,
    @SerializedName("action") val action: StepAction = StepAction.COMPLETE,
    @SerializedName("response_type") val responseType: ResponseType? = null,
    @SerializedName("menu_option") val menuOption: String? = null,
    @SerializedName("variable") val variable: String? = null,
    @SerializedName("next") val nextStepId: String? = null,
    @SerializedName("optional") val optional: Boolean = false,
    @SerializedName("skip_to") val skipToStepId: String? = null,
    @SerializedName("requires_live_verification") val requiresLiveVerification: Boolean = false,
    @SerializedName("success_prompts") val successPrompts: List<String> = emptyList(),
    @SerializedName("failure_prompts") val failurePrompts: List<String> = emptyList(),
    @SerializedName("pending_prompts") val pendingPrompts: List<String> = emptyList()
)

data class UssdFlowProfile(
    @SerializedName("flow_id") val flowId: String,
    @SerializedName("network") val network: String,
    @SerializedName("transaction_type") val transactionType: String,
    @SerializedName("version") val version: Int,
    @SerializedName("entry_code") val entryCode: String,
    @SerializedName("timeout_seconds") val timeoutSeconds: Int = 90,
    @SerializedName("enabled") val enabled: Boolean = false,
    @SerializedName("verified_with_sim") val verifiedWithSim: Boolean = false,
    @SerializedName("status") val configStatus: String = "VERIFIED",
    @SerializedName("steps") val steps: List<UssdFlowStep> = emptyList()
) {
    fun requiresSimVerification(): Boolean {
        return !verifiedWithSim || entryCode == VERIFY_PLACEHOLDER || !enabled
    }

    companion object {
        const val VERIFY_PLACEHOLDER = "VERIFY_FROM_AGENT_SIM"
    }
}

sealed interface UssdSessionState {
    data object Idle : UssdSessionState

    data class Initiating(
        val sessionId: String,
        val flowId: String
    ) : UssdSessionState

    data class AwaitingPrompt(
        val sessionId: String,
        val stepId: String,
        val statusMessage: String
    ) : UssdSessionState

    data class WaitingUserConfirmation(
        val sessionId: String,
        val stepId: String,
        val observedPrompt: String,
        val summaryText: String
    ) : UssdSessionState

    data class PausedForOperationalId(
        val sessionId: String,
        val stepId: String,
        val observedPrompt: String,
        val instruction: String = "Please enter the Telecel operational ID in the USSD window."
    ) : UssdSessionState

    data class PausedForPinAuthorization(
        val sessionId: String,
        val stepId: String,
        val observedPrompt: String
    ) : UssdSessionState

    data class WaitingForCustomerApproval(
        val sessionId: String,
        val stepId: String,
        val observedPrompt: String
    ) : UssdSessionState

    data class ManualInterventionRequired(
        val sessionId: String,
        val reason: String,
        val observedPrompt: String?
    ) : UssdSessionState

    data class ConfigurationRequired(
        val flowId: String,
        val message: String = "USSD CONFIGURATION REQUIRED — VERIFY WITH AUTHORIZED AGENT SIM"
    ) : UssdSessionState

    data class Completed(
        val sessionId: String,
        val outcome: UssdOutcome
    ) : UssdSessionState

    data class Cancelled(
        val sessionId: String,
        val reason: String
    ) : UssdSessionState

    data class TimedOut(
        val sessionId: String,
        val lastStepId: String?
    ) : UssdSessionState
}

enum class OutcomeCategory {
    SUCCESSFUL,
    FAILED,
    PENDING,
    INSUFFICIENT_BALANCE,
    INVALID_RECIPIENT,
    TIMEOUT,
    UNEXPECTED
}

data class UssdOutcome(
    val category: OutcomeCategory,
    val rawResponse: String,
    val providerReference: String? = null,
    val message: String
)
