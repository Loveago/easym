package com.easymomo.app.ussd.capture

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
    val isSensitive: Boolean = stepType == CustomStepPlaceholderType.MANUAL_AUTHORIZATION
)

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
