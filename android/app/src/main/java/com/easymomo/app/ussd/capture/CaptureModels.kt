package com.easymomo.app.ussd.capture

import com.easymomo.app.ussd.model.PromptMatchMode
import com.easymomo.app.ussd.model.ResponseType
import com.easymomo.app.ussd.model.StepAction
import com.easymomo.app.ussd.model.StepType
import com.easymomo.app.ussd.model.UssdFlowProfile
import com.easymomo.app.ussd.model.UssdFlowStep

enum class CaptureSessionStatus {
    RECORDING,
    PAUSED,
    COMPLETED,
    INTERRUPTED,
    CANCELLED,
    INCOMPLETE
}

enum class CaptureEventType {
    SESSION_STARTED,
    MENU_PROMPT,
    MENU_SELECTION,
    INPUT_PROMPT,
    INPUT_SUBMISSION,
    CONFIRMATION_PROMPT,
    RESULT_PROMPT,
    SESSION_ENDED,
    MANUAL_AUTHORIZATION,
    INACCESSIBLE_DIALOG,
    UNKNOWN_EVENT
}

enum class CaptureConfidence {
    HIGH,
    MEDIUM,
    LOW,
    SENSITIVE
}

enum class CustomFlowStatus(val label: String) {
    DRAFT("Draft"),
    INCOMPLETE("Incomplete"),
    DISABLED("Disabled"),
    VERIFIED("Verified")
}

enum class CustomStepPlaceholderType(val displayName: String) {
    MENU_SELECTION("Menu Selection"),
    CUSTOMER_NUMBER_PROMPT("Customer Number Prompt"),
    AMOUNT_PROMPT("Amount Prompt"),
    REFERENCE_PROMPT("Reference Prompt"),
    OPERATIONAL_ID_PROMPT("Operational ID Prompt"),
    MANUAL_AUTHORIZATION("Manual Authorization (PIN)"),
    CONFIRMATION_PROMPT("Confirmation Prompt"),
    RESULT_PROMPT("Result Prompt")
}

data class CustomFlowStepDraft(
    val sequence: Int,
    val stepType: CustomStepPlaceholderType,
    val label: String = stepType.displayName,
    val promptPreview: String = "",
    val isSensitive: Boolean = stepType == CustomStepPlaceholderType.MANUAL_AUTHORIZATION,
    val value: String = ""
)

enum class CustomUssdOperationType(val label: String) {
    STEP_BY_STEP("Step by Step"),
    ONE_TIME("One time")
}

data class SimpleCustomUssdFlow(
    val id: String,
    val name: String,
    val operationType: CustomUssdOperationType,
    val steps: List<String>,
    val oneTimeCode: String,
    val simSlotIndex: Int,
    val formulaPreview: String
)

object CustomUssdFlowBuilder {
    const val VAR_PHONE = "<phoneNumber>"
    const val VAR_AMOUNT = "<amount>"
    val QUICK_VARIABLES = listOf(VAR_PHONE, VAR_AMOUNT)

    private val pinHintRegex = Regex("""\b(?:pin|pincode|passcode|secret)\b""", RegexOption.IGNORE_CASE)

    fun formatPreview(
        operationType: CustomUssdOperationType,
        steps: List<String>,
        oneTimeCode: String
    ): String {
        return when (operationType) {
            CustomUssdOperationType.STEP_BY_STEP ->
                if (steps.isEmpty()) "None" else steps.joinToString(" , ")
            CustomUssdOperationType.ONE_TIME ->
                oneTimeCode.trim().ifEmpty { "None" }
        }
    }

    fun containsSensitivePinHint(text: String): Boolean {
        return pinHintRegex.containsMatchIn(text)
    }

    fun validate(
        name: String,
        operationType: CustomUssdOperationType,
        steps: List<String>,
        oneTimeCode: String
    ): String? {
        if (name.trim().isEmpty()) {
            return "Enter an Automation Name."
        }
        return when (operationType) {
            CustomUssdOperationType.STEP_BY_STEP -> {
                if (steps.isEmpty()) {
                    return "Add at least one USSD step (start with a USSD code like *171#)."
                }
                val first = steps.first().trim()
                if (!first.startsWith("*") || !first.endsWith("#")) {
                    return "Step 1 must be a USSD code starting with * and ending with # (e.g. *171#)."
                }
                if (steps.any { containsSensitivePinHint(it) }) {
                    return "For security reasons, do not include PIN codes in custom USSD steps."
                }
                null
            }
            CustomUssdOperationType.ONE_TIME -> {
                val trimmed = oneTimeCode.trim()
                if (trimmed.isEmpty()) {
                    return "Enter a One-Time USSD code (e.g. *124# or *171*1*<phoneNumber>*<amount>#)."
                }
                val stripped = trimmed
                    .replace(VAR_PHONE, "0", ignoreCase = true)
                    .replace(VAR_AMOUNT, "0", ignoreCase = true)
                if (!stripped.startsWith("*") || !stripped.endsWith("#")) {
                    return "One-Time USSD code must start with * and end with #."
                }
                if (containsSensitivePinHint(trimmed)) {
                    return "For security reasons, do not include PIN codes in custom USSD codes."
                }
                null
            }
        }
    }

    fun requiresPhone(flow: SimpleCustomUssdFlow): Boolean {
        return when (flow.operationType) {
            CustomUssdOperationType.STEP_BY_STEP ->
                flow.steps.any { it.contains(VAR_PHONE, ignoreCase = true) }
            CustomUssdOperationType.ONE_TIME ->
                flow.oneTimeCode.contains(VAR_PHONE, ignoreCase = true)
        }
    }

    fun requiresAmount(flow: SimpleCustomUssdFlow): Boolean {
        return when (flow.operationType) {
            CustomUssdOperationType.STEP_BY_STEP ->
                flow.steps.any { it.contains(VAR_AMOUNT, ignoreCase = true) }
            CustomUssdOperationType.ONE_TIME ->
                flow.oneTimeCode.contains(VAR_AMOUNT, ignoreCase = true)
        }
    }

    fun resolveOneTimeCode(template: String, phone: String, amount: String): String {
        return template.trim()
            .replace(VAR_PHONE, phone, ignoreCase = true)
            .replace(VAR_AMOUNT, amount, ignoreCase = true)
    }

    fun buildStepByStepProfile(flow: SimpleCustomUssdFlow): UssdFlowProfile {
        val entryCode = flow.steps.firstOrNull().orEmpty().trim()
        val replySteps = flow.steps.drop(1).map { it.trim() }.filter { it.isNotEmpty() }

        val steps = mutableListOf<UssdFlowStep>()
        steps += UssdFlowStep(
            id = "START",
            type = StepType.INITIATE_USSD,
            nextStepId = if (replySteps.isNotEmpty()) "CUSTOM_STEP_1" else null
        )

        replySteps.forEachIndexed { idx, rawToken ->
            val stepId = "CUSTOM_STEP_${idx + 1}"
            val nextId = if (idx + 1 < replySteps.size) "CUSTOM_STEP_${idx + 2}" else null
            steps += when {
                rawToken.equals(VAR_PHONE, ignoreCase = true) -> UssdFlowStep(
                    id = stepId,
                    type = StepType.MATCH_PROMPT,
                    expectedPrompts = listOf(
                        "number", "phone", "mobile", "recipient",
                        "re-enter", "reenter", "confirm", "repeat", "again", "merchant", "id"
                    ),
                    matchMode = PromptMatchMode.NORMALIZED_TEXT,
                    action = StepAction.SEND_VARIABLE,
                    responseType = ResponseType.TRANSACTION_CUSTOMER_PHONE,
                    variable = "customer_phone",
                    nextStepId = nextId
                )
                rawToken.equals(VAR_AMOUNT, ignoreCase = true) -> UssdFlowStep(
                    id = stepId,
                    type = StepType.MATCH_PROMPT,
                    expectedPrompts = listOf("amount", "ghs", "cedis", "enter"),
                    matchMode = PromptMatchMode.NORMALIZED_TEXT,
                    action = StepAction.SEND_VARIABLE,
                    responseType = ResponseType.TRANSACTION_AMOUNT,
                    variable = "amount",
                    nextStepId = nextId
                )
                else -> UssdFlowStep(
                    id = stepId,
                    type = StepType.MATCH_PROMPT,
                    expectedPrompts = listOf("$rawToken.", "$rawToken)"),
                    matchMode = PromptMatchMode.NORMALIZED_TEXT,
                    action = StepAction.SEND_MENU_OPTION,
                    responseType = ResponseType.STATIC_OPTION,
                    menuOption = rawToken,
                    nextStepId = nextId
                )
            }
        }

        return UssdFlowProfile(
            flowId = "CUSTOM_${flow.id}",
            network = "SIM_${flow.simSlotIndex + 1}",
            transactionType = "CUSTOM_USSD",
            version = 1,
            entryCode = entryCode,
            timeoutSeconds = 90,
            enabled = true,
            verifiedWithSim = true,
            configStatus = "CUSTOM",
            steps = steps
        )
    }
}

data class CapturedNodeSnapshot(
    val packageName: String,
    val promptText: String,
    val visibleButtons: List<String> = emptyList(),
    val hasEditableInput: Boolean = false,
    val isPasswordInput: Boolean = false,
    val inputNodeClass: String? = null,
    val rootNodeClass: String? = null,
    val nodeMetadataSummary: String = "",
    val clickedButtonLabel: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
