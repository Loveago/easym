package com.easymomo.app.ussd

import com.easymomo.app.ussd.model.OutcomeCategory
import com.easymomo.app.ussd.model.ResponseType
import com.easymomo.app.ussd.model.StepAction
import com.easymomo.app.ussd.model.StepType
import com.easymomo.app.ussd.model.UssdFlowProfile
import com.easymomo.app.ussd.model.UssdFlowStep
import com.easymomo.app.ussd.model.UssdOutcome
import com.easymomo.app.ussd.model.UssdSessionState

/**
 * Abstraction for sending a USSD reply (e.g., via AccessibilityService or user-assisted prompt).
 */
fun interface UssdResponseSender {
    fun sendReply(replyText: String): Boolean
}

/**
 * State-driven USSD Flow Controller.
 * Advances to the next step ONLY after observing and matching the expected prompt.
 */
class UssdFlowController(
    private val sessionManager: UssdSessionManager = UssdSessionManager(),
    private val promptMatcher: UssdPromptMatcher = UssdPromptMatcher(),
    private val inputHandler: UssdInputHandler = UssdInputHandler(),
    private val confirmationHandler: UssdConfirmationHandler = UssdConfirmationHandler(),
    private val resultDetector: UssdResultDetector = UssdResultDetector(),
    private val recoveryManager: UssdRecoveryManager = UssdRecoveryManager(),
    private val responseSender: UssdResponseSender = UssdResponseSender { false }
) {
    private var activeProfile: UssdFlowProfile? = null
    private var activeVariables: UssdInputHandler.TransactionVariables? = null
    private var currentStep: UssdFlowStep? = null
    private var lastSubmittedStepId: String? = null
    private var lastSubmittedPromptNormalized: String? = null
    private val submittedPromptsHistory = mutableSetOf<String>()

    val sessionState = sessionManager.state

    fun startFlow(
        transactionLocalId: String,
        profile: UssdFlowProfile,
        variables: UssdInputHandler.TransactionVariables
    ): Result<String> {
        if (profile.requiresSimVerification()) {
            sessionManager.updateState(UssdSessionState.ConfigurationRequired(profile.flowId))
            return Result.failure(
                IllegalStateException("USSD CONFIGURATION REQUIRED — VERIFY WITH AUTHORIZED AGENT SIM")
            )
        }

        val sessionId = sessionManager.startSession(transactionLocalId, profile.flowId)
            ?: return Result.failure(IllegalStateException("Another USSD session is already active"))

        activeProfile = profile
        activeVariables = variables
        lastSubmittedStepId = null
        lastSubmittedPromptNormalized = null
        submittedPromptsHistory.clear()
        val startStep = profile.steps.firstOrNull { it.type == StepType.INITIATE_USSD }
            ?: profile.steps.firstOrNull()

        if (startStep == null) {
            sessionManager.endSession(
                UssdSessionState.ManualInterventionRequired(sessionId, "Flow has no steps", null)
            )
            return Result.failure(IllegalStateException("Flow ${profile.flowId} has no steps"))
        }

        val nextStep = startStep.nextStepId?.let { id -> profile.steps.firstOrNull { it.id == id } } ?: startStep
        currentStep = nextStep
        sessionManager.updateStep(
            nextStep.id,
            UssdSessionState.AwaitingPrompt(sessionId, nextStep.id, "Initiated ${profile.entryCode}. Waiting for network prompt...")
        )
        return Result.success(sessionId)
    }

    fun onPromptObserved(observedRaw: String) {
        val sessionId = sessionManager.getActiveSessionId() ?: return
        val profile = activeProfile ?: return
        val step = currentStep ?: return
        val vars = activeVariables ?: return
        val observedNorm = promptMatcher.normalize(observedRaw)

        // Prevent duplicate/stale submissions if any already-submitted prompt fires again after step advancement
        if ((lastSubmittedPromptNormalized != null && observedNorm == lastSubmittedPromptNormalized) ||
            observedNorm in submittedPromptsHistory
        ) {
            UssdDiagnosticLog.log("MATCH", "Duplicate/stale prompt ignored (already submitted $lastSubmittedStepId)")
            return
        }

        // If currently paused for manual PIN, resume only when a subsequent non-PIN prompt arrives
        if (sessionManager.state.value is UssdSessionState.PausedForPinAuthorization) {
            val quickMatch = promptMatcher.matchStep(observedRaw, step)
            if (quickMatch is UssdPromptMatcher.MatchResult.TransientLoading) return
            if (confirmationHandler.isPinKeywordPrompt(observedRaw)) return
            resumeAfterManualPin(observedRaw)
            return
        }

        // If currently paused for manual operational ID, resume only when the next prompt is detected
        if (sessionManager.state.value is UssdSessionState.PausedForOperationalId) {
            val quickMatch = promptMatcher.matchStep(observedRaw, step)
            if (quickMatch is UssdPromptMatcher.MatchResult.TransientLoading) return
            if (quickMatch is UssdPromptMatcher.MatchResult.Matched) return
            resumeAfterManualOperationalId(observedRaw)
            return
        }

        // 1. Check if this is the final result step
        if (step.type == StepType.MATCH_RESULT || step.responseType == ResponseType.TERMINAL_RESULT) {
            val outcome = resultDetector.detect(observedRaw, step)
            handleTerminalOutcome(sessionId, profile, step, outcome, observedRaw)
            return
        }

        // 2. Check if provider is asking for PIN/OTP authorization (never automate PIN)
        if (confirmationHandler.requiresPinAuthorization(observedRaw, step)) {
            if (step.type != StepType.REQUIRE_AUTHORIZATION && step.optional &&
                tryAdvanceAcrossOptionalSteps(sessionId, profile, step, vars, observedRaw)
            ) {
                return
            }
            if (step.action == StepAction.WAIT_FOR_PROMPT || step.responseType == ResponseType.WAIT_FOR_PROMPT) {
                val pinStep = step.nextStepId?.let { id -> profile.steps.firstOrNull { it.id == id } }
                if (pinStep != null && (pinStep.action == StepAction.PAUSE_FOR_PIN || pinStep.responseType == ResponseType.MANUAL_SENSITIVE_INPUT)) {
                    currentStep = pinStep
                }
            }
            UssdDiagnosticLog.log("STATE", "Paused for agent PIN at step ${currentStep?.id ?: step.id}")
            sessionManager.updateState(
                UssdSessionState.PausedForPinAuthorization(sessionId, currentStep?.id ?: step.id, observedRaw)
            )
            return
        }

        // 2b. Handle WAIT_FOR_PROMPT steps (e.g., incomplete flows requiring live verification)
        if (step.action == StepAction.WAIT_FOR_PROMPT || step.responseType == ResponseType.WAIT_FOR_PROMPT) {
            when (val waitMatch = promptMatcher.matchStep(observedRaw, step)) {
                is UssdPromptMatcher.MatchResult.TransientLoading -> return
                is UssdPromptMatcher.MatchResult.ProviderError -> {
                    val outcome = resultDetector.detect(observedRaw, step)
                    handleTerminalOutcome(sessionId, profile, step, outcome, observedRaw)
                    return
                }
                else -> {
                    UssdDiagnosticLog.log("STATE", "Paused at WAIT_FOR_PROMPT (${step.id}) for live verification")
                    sessionManager.endSession(
                        UssdSessionState.ManualInterventionRequired(
                            sessionId,
                            "Remaining prompts require live SIM verification. Complete manually in the USSD window.",
                            observedRaw
                        )
                    )
                    return
                }
            }
        }

        // 3. Verify observed prompt matches expected prompt for current step
        when (val match = promptMatcher.matchStep(observedRaw, step)) {
            is UssdPromptMatcher.MatchResult.TransientLoading -> {
                UssdDiagnosticLog.log("MATCH", "Transient loading dialog ignored at ${step.id}")
                return
            }
            is UssdPromptMatcher.MatchResult.Matched -> {
                UssdDiagnosticLog.log("MATCH", "Matched step ${step.id} via '${match.matchedPattern}'")
                handleMatchedStep(sessionId, profile, step, vars, observedRaw)
            }
            is UssdPromptMatcher.MatchResult.ProviderError -> {
                val outcome = resultDetector.detect(observedRaw, step)
                handleTerminalOutcome(sessionId, profile, step, outcome, observedRaw)
            }
            is UssdPromptMatcher.MatchResult.SessionTerminated -> {
                val decision = recoveryManager.onNetworkOrAppInterruption(sessionManager.hasSentFinancialInput())
                sessionManager.endSession(
                    UssdSessionState.ManualInterventionRequired(sessionId, decision.userMessage, observedRaw)
                )
            }
            is UssdPromptMatcher.MatchResult.UnexpectedPrompt -> {
                // If current step is optional, check whether the network skipped to a subsequent step
                if (step.optional && tryAdvanceAcrossOptionalSteps(sessionId, profile, step, vars, observedRaw)) {
                    return
                }
                if (observedNorm.length < 6 || observedNorm.contains("invalid") || observedNorm.contains("try again")) {
                    UssdDiagnosticLog.log("MATCH", "Ignored non-advancing prompt at ${step.id}: ${observedNorm.take(40)}")
                    return
                }
                val isVariableStep = step.action == StepAction.SEND_VARIABLE ||
                    step.responseType == ResponseType.TRANSACTION_CUSTOMER_PHONE ||
                    step.responseType == ResponseType.TRANSACTION_AMOUNT
                val looksLikeMenu = observedNorm.contains("1)") || observedNorm.contains("1.") ||
                    observedNorm.contains("2)") || observedNorm.contains("2.")
                if (isVariableStep && looksLikeMenu) {
                    UssdDiagnosticLog.log("MATCH", "Ignored menu-like prompt while waiting for input at ${step.id}")
                    return
                }
                UssdDiagnosticLog.log("MATCH", "Proceeding with configured step ${step.id} on prompt")
                handleMatchedStep(sessionId, profile, step, vars, observedRaw)
            }
        }
    }

    private fun tryAdvanceAcrossOptionalSteps(
        sessionId: String,
        profile: UssdFlowProfile,
        fromStep: UssdFlowStep,
        vars: UssdInputHandler.TransactionVariables,
        observedRaw: String
    ): Boolean {
        var cursor: UssdFlowStep? = fromStep
        val visited = mutableSetOf<String>()
        while (cursor != null && cursor.optional && visited.add(cursor.id)) {
            val nextId = cursor.skipToStepId ?: cursor.nextStepId ?: break
            val candidate = profile.steps.firstOrNull { it.id == nextId } ?: break

            if (confirmationHandler.requiresPinAuthorization(observedRaw, candidate)) {
                UssdDiagnosticLog.log("MATCH", "Skipped optional ${fromStep.id} -> PIN at ${candidate.id}")
                currentStep = candidate
                sessionManager.updateState(
                    UssdSessionState.PausedForPinAuthorization(sessionId, candidate.id, observedRaw)
                )
                return true
            }

            if (candidate.type == StepType.MATCH_RESULT || candidate.responseType == ResponseType.TERMINAL_RESULT) {
                currentStep = candidate
                val outcome = resultDetector.detect(observedRaw, candidate)
                handleTerminalOutcome(sessionId, profile, candidate, outcome, observedRaw)
                return true
            }

            val candidateMatch = promptMatcher.matchStep(observedRaw, candidate)
            if (candidateMatch is UssdPromptMatcher.MatchResult.Matched) {
                UssdDiagnosticLog.log("MATCH", "Skipped optional ${fromStep.id} -> matched ${candidate.id}")
                currentStep = candidate
                handleMatchedStep(sessionId, profile, candidate, vars, observedRaw)
                return true
            }
            cursor = candidate
        }
        return false
    }

    private fun handleMatchedStep(
        sessionId: String,
        profile: UssdFlowProfile,
        step: UssdFlowStep,
        vars: UssdInputHandler.TransactionVariables,
        observedRaw: String
    ) {
        val effectiveAction = when (step.responseType) {
            ResponseType.STATIC_OPTION -> StepAction.SEND_MENU_OPTION
            ResponseType.TRANSACTION_CUSTOMER_PHONE,
            ResponseType.TRANSACTION_AMOUNT -> StepAction.SEND_VARIABLE
            ResponseType.MANUAL_OPERATIONAL_ID -> StepAction.PAUSE_FOR_OPERATIONAL_ID
            ResponseType.MANUAL_REFERENCE -> StepAction.PAUSE_FOR_REFERENCE
            ResponseType.MANUAL_SENSITIVE_INPUT -> StepAction.PAUSE_FOR_PIN
            ResponseType.WAIT_FOR_PROMPT -> StepAction.WAIT_FOR_PROMPT
            ResponseType.TERMINAL_RESULT -> StepAction.COMPLETE
            null -> step.action
        }
        when (effectiveAction) {
            StepAction.SEND_MENU_OPTION -> {
                val option = step.menuOption
                if (option.isNullOrBlank() || option == UssdFlowProfile.VERIFY_PLACEHOLDER) {
                    sessionManager.endSession(
                        UssdSessionState.ConfigurationRequired(profile.flowId)
                    )
                    return
                }
                sendAndAdvance(sessionId, profile, step, option, isFinancialInput = false, observedRaw = observedRaw)
            }
            StepAction.SEND_VARIABLE -> {
                val varName = when (step.responseType) {
                    ResponseType.TRANSACTION_CUSTOMER_PHONE -> step.variable ?: "customer_phone"
                    ResponseType.TRANSACTION_AMOUNT -> step.variable ?: "amount"
                    else -> step.variable ?: ""
                }
                when (val resolved = inputHandler.resolveVariable(varName, vars)) {
                    is UssdInputHandler.ValidationResult.Invalid -> {
                        sessionManager.endSession(
                            UssdSessionState.ManualInterventionRequired(sessionId, resolved.reason, observedRaw)
                        )
                    }
                    is UssdInputHandler.ValidationResult.Valid -> {
                        val isFinancial = varName == "amount" || varName == "customer_phone" || varName == "recipient_phone"
                        sendAndAdvance(sessionId, profile, step, resolved.value, isFinancialInput = isFinancial, observedRaw = observedRaw)
                    }
                }
            }
            StepAction.REQUIRE_CONFIRMATION -> {
                val summary = confirmationHandler.buildConfirmationSummary(vars, observedRaw)
                sessionManager.updateState(
                    UssdSessionState.WaitingUserConfirmation(sessionId, step.id, observedRaw, summary)
                )
            }
            StepAction.PAUSE_FOR_OPERATIONAL_ID -> {
                if (vars.operationalId.isNotBlank()) {
                    when (val resolved = inputHandler.resolveVariable("operational_id", vars)) {
                        is UssdInputHandler.ValidationResult.Valid -> {
                            UssdDiagnosticLog.log("STATE", "Auto-entering saved Telecel operational ID at step ${step.id}")
                            sendAndAdvance(sessionId, profile, step, resolved.value, isFinancialInput = false, observedRaw = observedRaw)
                            return
                        }
                        is UssdInputHandler.ValidationResult.Invalid -> {
                            UssdDiagnosticLog.log("STATE", "Saved Telecel operational ID invalid (${resolved.reason}); pausing for manual entry")
                        }
                    }
                }
                UssdDiagnosticLog.log("STATE", "Paused for manual Telecel operational ID at step ${step.id}")
                sessionManager.updateState(
                    UssdSessionState.PausedForOperationalId(sessionId, step.id, observedRaw)
                )
            }
            StepAction.PAUSE_FOR_REFERENCE -> {
                UssdDiagnosticLog.log("STATE", "Paused for manual reference at step ${step.id}")
                sessionManager.updateState(
                    UssdSessionState.PausedForOperationalId(
                        sessionId = sessionId,
                        stepId = step.id,
                        observedPrompt = observedRaw,
                        instruction = "Please enter the payment reference in the USSD window."
                    )
                )
            }
            StepAction.PAUSE_FOR_PIN -> {
                sessionManager.updateState(
                    UssdSessionState.PausedForPinAuthorization(sessionId, step.id, observedRaw)
                )
            }
            StepAction.WAIT_FOR_PROMPT -> {
                sessionManager.endSession(
                    UssdSessionState.ManualInterventionRequired(
                        sessionId,
                        "Remaining prompts require live SIM verification. Complete manually in the USSD window.",
                        observedRaw
                    )
                )
            }
            StepAction.COMPLETE -> {
                val outcome = resultDetector.detect(observedRaw, step)
                handleTerminalOutcome(sessionId, profile, step, outcome, observedRaw)
            }
        }
    }

    fun confirmPendingStep() {
        val sessionId = sessionManager.getActiveSessionId() ?: return
        val profile = activeProfile ?: return
        val step = currentStep ?: return
        val reply = step.menuOption ?: "1"
        sendAndAdvance(sessionId, profile, step, reply, isFinancialInput = true)
    }

    fun resumeAfterManualOperationalId(nextObservedPrompt: String) {
        val sessionId = sessionManager.getActiveSessionId() ?: return
        val profile = activeProfile ?: return
        val step = currentStep ?: return
        val next = step.nextStepId?.let { id -> profile.steps.firstOrNull { it.id == id } }
        if (next != null) {
            currentStep = next
            sessionManager.updateStep(
                next.id,
                UssdSessionState.AwaitingPrompt(sessionId, next.id, "Resuming after operational ID...")
            )
            onPromptObserved(nextObservedPrompt)
        } else {
            val outcome = resultDetector.detect(nextObservedPrompt, step)
            handleTerminalOutcome(sessionId, profile, step, outcome, nextObservedPrompt)
        }
    }

    fun resumeAfterManualPin(nextObservedPrompt: String) {
        val sessionId = sessionManager.getActiveSessionId() ?: return
        val profile = activeProfile ?: return
        val step = currentStep ?: return
        val next = step.nextStepId?.let { id -> profile.steps.firstOrNull { it.id == id } }
        if (next != null) {
            currentStep = next
            sessionManager.updateStep(
                next.id,
                UssdSessionState.AwaitingPrompt(sessionId, next.id, "Resuming after authorization...")
            )
            onPromptObserved(nextObservedPrompt)
        } else {
            val outcome = resultDetector.detect(nextObservedPrompt, step)
            handleTerminalOutcome(sessionId, profile, step, outcome, nextObservedPrompt)
        }
    }

    fun cancelSession() {
        val sessionId = sessionManager.getActiveSessionId() ?: return
        val decision = recoveryManager.onUserCancelled(sessionManager.hasSentFinancialInput())
        activeProfile = null
        activeVariables = null
        currentStep = null
        lastSubmittedStepId = null
        lastSubmittedPromptNormalized = null
        submittedPromptsHistory.clear()
        sessionManager.endSession(UssdSessionState.Cancelled(sessionId, decision.userMessage))
    }

    fun onTimeout() {
        val sessionId = sessionManager.getActiveSessionId() ?: return
        val lastStep = currentStep?.id
        activeProfile = null
        activeVariables = null
        currentStep = null
        lastSubmittedStepId = null
        lastSubmittedPromptNormalized = null
        submittedPromptsHistory.clear()
        sessionManager.endSession(UssdSessionState.TimedOut(sessionId, lastStep))
    }

    private fun sendAndAdvance(
        sessionId: String,
        profile: UssdFlowProfile,
        step: UssdFlowStep,
        reply: String,
        isFinancialInput: Boolean,
        observedRaw: String? = null
    ) {
        if (isFinancialInput) {
            sessionManager.markFinancialInputSent()
        }
        val sent = responseSender.sendReply(reply)
        if (!sent) {
            UssdDiagnosticLog.log("SEND", "Could not auto-submit step ${step.id}; requesting manual entry")
            sessionManager.endSession(
                UssdSessionState.ManualInterventionRequired(
                    sessionId,
                    "Automatic entry unavailable on this prompt. Please enter '$reply' in the USSD dialog to continue.",
                    null
                )
            )
            return
        }
        lastSubmittedStepId = step.id
        lastSubmittedPromptNormalized = observedRaw?.let { promptMatcher.normalize(it) }
        lastSubmittedPromptNormalized?.let { submittedPromptsHistory.add(it) }
        UssdDiagnosticLog.log("SEND", "Auto-submitted step ${step.id} (${if (isFinancialInput) "tx-field" else "menu"})")
        val next = step.nextStepId?.let { id -> profile.steps.firstOrNull { it.id == id } }
        if (next != null) {
            currentStep = next
            sessionManager.updateStep(
                next.id,
                UssdSessionState.AwaitingPrompt(sessionId, next.id, "Waiting for next prompt...")
            )
        }
    }

    private fun handleTerminalOutcome(
        sessionId: String,
        profile: UssdFlowProfile,
        step: UssdFlowStep,
        outcome: UssdOutcome,
        observedRaw: String
    ) {
        if (outcome.category == OutcomeCategory.PENDING && profile.transactionType == "CASH_OUT") {
            UssdDiagnosticLog.log("STATE", "Cash Out awaiting customer MoMo PIN approval")
            activeProfile = null
            activeVariables = null
            currentStep = null
            sessionManager.endSession(
                UssdSessionState.WaitingForCustomerApproval(sessionId, step.id, observedRaw)
            )
            return
        }
        completeSession(sessionId, outcome)
    }

    private fun completeSession(sessionId: String, outcome: UssdOutcome) {
        activeProfile = null
        activeVariables = null
        currentStep = null
        sessionManager.endSession(UssdSessionState.Completed(sessionId, outcome))
    }
}
