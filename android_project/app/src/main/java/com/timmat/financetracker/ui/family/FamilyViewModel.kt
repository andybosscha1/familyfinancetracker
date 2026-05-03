package com.timmat.financetracker.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timmat.financetracker.data.model.Category
import com.timmat.financetracker.data.model.FamilyMember
import com.timmat.financetracker.data.model.Invitation
import com.timmat.financetracker.data.model.Role
import com.timmat.financetracker.data.repository.AuthRepository
import com.timmat.financetracker.data.repository.CategoryRepository
import com.timmat.financetracker.data.repository.FamilyRepository
import com.timmat.financetracker.data.repository.InvitationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FamilyUiState(
    val isAdmin: Boolean = false,
    val members: List<FamilyMember> = emptyList(),
    val invitations: List<Invitation> = emptyList(),
    val categories: List<Category> = emptyList(),
    val error: String? = null,
    val info: String? = null,
)

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val invitationRepository: InvitationRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FamilyUiState())
    val state: StateFlow<FamilyUiState> = _state.asStateFlow()

    fun load(familyId: String) {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid.orEmpty()
            val role = familyRepository.currentUserRole(familyId, uid)
            _state.update { it.copy(isAdmin = role == Role.admin) }

            launch {
                familyRepository.observeMembers(familyId).collect { list ->
                    _state.update { it.copy(members = list) }
                }
            }
            launch {
                invitationRepository.observeInvitationsForFamily(familyId).collect { list ->
                    _state.update { it.copy(invitations = list) }
                }
            }
            launch {
                categoryRepository.observe(familyId).collect { list ->
                    _state.update { it.copy(categories = list) }
                }
            }
        }
    }

    fun invite(familyId: String, email: String) {
        viewModelScope.launch {
            runCatching { invitationRepository.invite(familyId, email) }
                .onSuccess { _state.update { it.copy(info = "Invitation sent") } }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
        }
    }

    fun removeMember(familyId: String, userId: String) {
        viewModelScope.launch {
            runCatching { familyRepository.removeMember(familyId, userId) }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
        }
    }

    fun addCategory(familyId: String, name: String) {
        viewModelScope.launch {
            runCatching { categoryRepository.add(familyId, name) }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            runCatching { categoryRepository.delete(categoryId) }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
        }
    }

    fun cancelInvite(invitationId: String) {
        viewModelScope.launch {
            runCatching { invitationRepository.cancel(invitationId) }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, info = null) }
}
