package com.timmat.financetracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timmat.financetracker.data.model.AppLockMode
import com.timmat.financetracker.data.model.Family
import com.timmat.financetracker.data.repository.AuthRepository
import com.timmat.financetracker.data.repository.FamilyRepository
import com.timmat.financetracker.data.repository.InvitationRepository
import com.timmat.financetracker.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val checking: Boolean = true,
    val families: List<Family> = emptyList(),
    val error: String? = null,
    val info: String? = null,
    val busy: Boolean = false,
    val requestSubmitted: Boolean = false,
    val shouldPromptAppLock: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val invitationRepository: InvitationRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        val user = authRepository.currentUser
        if (user == null) {
            _state.update { it.copy(checking = false, error = "Not signed in") }
        } else {
            val promptNeeded = settingsRepository.appLock == AppLockMode.None &&
                !settingsRepository.appLockPromptShown
            _state.update { it.copy(shouldPromptAppLock = promptNeeded) }
            viewModelScope.launch {
                familyRepository.observeFamiliesForUser(user.uid).collect { list ->
                    _state.update { it.copy(checking = false, families = list) }
                }
            }
        }
    }

    fun markAppLockPromptShown() {
        settingsRepository.appLockPromptShown = true
        _state.update { it.copy(shouldPromptAppLock = false) }
    }

    fun createFamily(name: String, onCreated: (String) -> Unit) {
        val user = authRepository.currentUser ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) { _state.update { it.copy(error = "Family name required") }; return }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, info = null) }
            runCatching { familyRepository.createFamily(trimmed, user.uid) }
                .onSuccess { id -> _state.update { it.copy(busy = false) }; onCreated(id) }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun submitJoinRequest(code: String) {
        val user = authRepository.currentUser ?: return
        val trimmed = code.trim()
        if (trimmed.length != 6 || trimmed.any { !it.isDigit() }) {
            _state.update { it.copy(error = "Enter the 6-digit code") }; return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, info = null) }
            runCatching {
                invitationRepository.submitRequest(
                    code = trimmed,
                    userId = user.uid,
                    userName = user.displayName.orEmpty(),
                    userEmail = user.email.orEmpty(),
                )
            }.onSuccess {
                _state.update { it.copy(busy = false, requestSubmitted = true, info = "Request submitted.") }
            }.onFailure { e ->
                _state.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, info = null) }

    fun signOut() = viewModelScope.launch { authRepository.signOut() }
}
