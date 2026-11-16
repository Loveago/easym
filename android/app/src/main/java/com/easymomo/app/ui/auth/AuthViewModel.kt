package com.easymomo.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.easymomo.app.data.remote.dto.PaymentHistoryDto
import com.easymomo.app.data.remote.dto.SubscriptionPlanDto
import com.easymomo.app.data.repository.AuthSubscriptionRepository
import com.easymomo.app.domain.model.NetworkPrefixDetector
import com.easymomo.app.domain.model.SubscriptionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val plans: List<SubscriptionPlanDto> = emptyList(),
    val paymentHistory: List<PaymentHistoryDto> = emptyList(),
    val pendingCheckoutUrl: String? = null,
    val pendingReference: String? = null,
    val checkoutStatusMessage: String? = null
)

class AuthViewModel(
    private val repo: AuthSubscriptionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val currentUser = repo.currentUser
    val subscription: StateFlow<SubscriptionInfo> = repo.subscription

    fun isLoggedIn(): Boolean = repo.isLoggedIn()
    fun isOnboardingCompleted(): Boolean = repo.isOnboardingCompleted()
    fun completeOnboarding() = repo.setOnboardingCompleted()

    fun loginAsDebugGuest(): Boolean {
        repo.ensureDebugGuestSession()
        return repo.isLoggedIn()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun login(identifier: String, password: String, onSuccess: () -> Unit) {
        if (identifier.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your email/phone and password.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repo.login(identifier, password)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Login failed") }
                }
        }
    }

    fun register(
        fullName: String,
        phone: String,
        email: String,
        password: String,
        confirmPassword: String,
        onSuccess: () -> Unit
    ) {
        when {
            fullName.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Full name is required.") }
                return
            }
            !NetworkPrefixDetector.isValidGhanaPhone(phone) -> {
                _uiState.update { it.copy(errorMessage = "Enter a valid 10-digit Ghana phone number.") }
                return
            }
            !email.contains("@") || !email.contains(".") -> {
                _uiState.update { it.copy(errorMessage = "Enter a valid email address.") }
                return
            }
            password.length < 8 -> {
                _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters.") }
                return
            }
            password != confirmPassword -> {
                _uiState.update { it.copy(errorMessage = "Passwords do not match.") }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repo.register(fullName, NetworkPrefixDetector.normalizeGhanaPhone(phone), email, password)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Registration failed") }
                }
        }
    }

    fun loadSubscriptionDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repo.refreshSubscriptionStatus()
            val plansResult = repo.getPlans()
            val historyResult = repo.getPaymentHistory()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    plans = plansResult.getOrDefault(it.plans),
                    paymentHistory = historyResult.getOrDefault(it.paymentHistory),
                    errorMessage = plansResult.exceptionOrNull()?.message
                )
            }
        }
    }

    fun startPaystackCheckout(planId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, checkoutStatusMessage = null) }
            repo.initializePaystackCheckout(planId)
                .onSuccess { init ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingCheckoutUrl = init.authorizationUrl,
                            pendingReference = init.reference
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.message ?: "Failed to initialize Paystack")
                    }
                }
        }
    }

    fun consumeCheckoutUrl() {
        _uiState.update { it.copy(pendingCheckoutUrl = null) }
    }

    fun verifyPendingOrRefresh(reference: String? = _uiState.value.pendingReference) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            if (!reference.isNullOrBlank()) {
                repo.verifyPaystackPayment(reference)
                    .onSuccess {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                pendingReference = null,
                                checkoutStatusMessage = "Payment verified and subscription updated."
                            )
                        }
                        loadSubscriptionDetails()
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = err.message ?: "Payment verification pending. Pull to refresh."
                            )
                        }
                    }
            } else {
                repo.refreshSubscriptionStatus()
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        repo.logout()
        onDone()
    }

    class Factory(private val repo: AuthSubscriptionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(repo) as T
    }
}
