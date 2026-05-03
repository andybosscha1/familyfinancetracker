package com.timmat.financetracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timmat.financetracker.data.model.Family
import com.timmat.financetracker.data.repository.AuthRepository
import com.timmat.financetracker.data.repository.FamilyRepository
import com.timmat.financetracker.data.repository.InvitationRepository
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
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val invitationRepository: InvitationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init { observeFamilies() }

    private fun observeFamilies() {
        val user = authRepository.currentUser ?: run {
            _state.update { it.copy(checking = false, error = "Not signed in") }
            return
        }
        viewModelScope.launch {
            familyRepository.observeFamiliesForUser(user.uid).collect { list ->
                _state.update { it.copy(checking = false, families = list) }
            }
        }
    }

    /** Creates a new family and makes the caller its admin. */
    fun createFamily(name: String, onCreated: (String) -> Unit) {
        val user = authRepository.currentUser ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(error = "Family name required") }; return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, info = null) }
            runCatching { familyRepository.createFamily(trimmed, user.uid) }
                .onSuccess { familyId ->
                    _state.update { it.copy(busy = false) }
                    onCreated(familyId)
                }
                .onFailure { err ->
                    _state.update { it.copy(busy = false, error = err.message ?: "Failed to create family") }
                }
        }
    }

    /** Joins a family using a 6-digit invitation code. */
    fun joinByCode(code: String, onJoined: (String) -> Unit) {
        val user = authRepository.currentUser ?: return
        val trimmed = code.trim()
        if (trimmed.length != 6 || trimmed.any { !it.isDigit() }) {
            _state.update { it.copy(error = "Enter the 6-digit code from your invitation email") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, info = null) }
            runCatching { invitationRepository.acceptByCode(user.uid, trimmed) }
                .onSuccess { familyId ->
                    _state.update { it.copy(busy = false, info = "Joined!") }
                    onJoined(familyId)
                }
                .onFailure { err ->
                    _state.update { it.copy(busy = false, error = err.message ?: "Could not join") }
                }
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, info = null) }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
