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
    val creating: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val invitationRepository: InvitationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * 1. Accept any pending invitations for this user's email.
     * 2. Fetch the (now updated) list of families the user belongs to.
     * 3. If exactly one family exists, the UI auto-navigates into it.
     */
    fun refresh() {
        val user = authRepository.currentUser ?: run {
            _state.update { it.copy(checking = false, error = "Not signed in") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(checking = true, error = null) }
            runCatching {
                invitationRepository.processPendingInvitationsForUser(
                    userId = user.uid,
                    email = user.email.orEmpty(),
                )
                familyRepository.observeFamiliesForUser(user.uid)
            }.onSuccess { flow ->
                flow.collect { list ->
                    _state.update { it.copy(checking = false, families = list) }
                }
            }.onFailure { err ->
                _state.update { it.copy(checking = false, error = err.message) }
            }
        }
    }

    fun createFamily(name: String, onCreated: (String) -> Unit) {
        val user = authRepository.currentUser ?: return
        if (name.isBlank()) {
            _state.update { it.copy(error = "Family name required") }; return
        }
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = null) }
            runCatching { familyRepository.createFamily(name, user.uid) }
                .onSuccess { id ->
                    _state.update { it.copy(creating = false) }
                    onCreated(id)
                }
                .onFailure { err ->
                    _state.update { it.copy(creating = false, error = err.message) }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
