package com.easymomo.app.ussd

import com.easymomo.app.ussd.model.UssdSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Tracks active USSD session state, prevents overlapping transactions,
 * and prevents accidental duplicate execution.
 */
class UssdSessionManager {

    private val _state = MutableStateFlow<UssdSessionState>(UssdSessionState.Idle)
    val state: StateFlow<UssdSessionState> = _state.asStateFlow()

    @Volatile
    private var activeSessionId: String? = null

    @Volatile
    private var activeTransactionLocalId: String? = null

    @Volatile
    private var currentStepId: String? = null

    @Volatile
    private var financialInputSent: Boolean = false

    @Synchronized
    fun startSession(transactionLocalId: String, flowId: String): String? {
        if (activeSessionId != null) {
            return null // Prevent overlapping USSD transactions
        }
        val newId = UUID.randomUUID().toString()
        activeSessionId = newId
        activeTransactionLocalId = transactionLocalId
        currentStepId = null
        financialInputSent = false
        _state.value = UssdSessionState.Initiating(newId, flowId)
        return newId
    }

    fun getActiveSessionId(): String? = activeSessionId
    fun getActiveTransactionLocalId(): String? = activeTransactionLocalId
    fun getCurrentStepId(): String? = currentStepId
    fun hasSentFinancialInput(): Boolean = financialInputSent

    fun markFinancialInputSent() {
        financialInputSent = true
    }

    fun updateStep(stepId: String, newState: UssdSessionState) {
        currentStepId = stepId
        _state.value = newState
    }

    fun updateState(newState: UssdSessionState) {
        _state.value = newState
    }

    @Synchronized
    fun endSession(finalState: UssdSessionState) {
        _state.value = finalState
        activeSessionId = null
        activeTransactionLocalId = null
        currentStepId = null
    }

    @Synchronized
    fun resetToIdle() {
        activeSessionId = null
        activeTransactionLocalId = null
        currentStepId = null
        financialInputSent = false
        _state.value = UssdSessionState.Idle
    }
}
