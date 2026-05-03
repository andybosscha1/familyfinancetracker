package com.timmat.financetracker.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timmat.financetracker.data.model.Category
import com.timmat.financetracker.data.model.Family
import com.timmat.financetracker.data.model.FamilyMember
import com.timmat.financetracker.data.model.Invitation
import com.timmat.financetracker.data.model.Role
import com.timmat.financetracker.data.model.User
import com.timmat.financetracker.data.repository.AuthRepository
import com.timmat.financetracker.data.repository.CategoryRepository
import com.timmat.financetracker.data.repository.FamilyRepository
import com.timmat.financetracker.data.repository.InvitationRepository
import com.timmat.financetracker.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FamilyUiState(
    val isAdmin: Boolean = false,
    /** True iff the current user is the original creator of the family (only they can delete it). */
    val isCreator: Boolean = false,
    val family: Family? = null,
    val members: List<FamilyMember> = emptyList(),
    /** Map of userId -> resolved User profile (firstName/lastName/email). Missing users render as email/uid fallback. */
    val profiles: Map<String, User> = emptyMap(),
    val invitations: List<Invitation> = emptyList(),
    val categories: List<Category> = emptyList(),
    val error: String? = null,
    val info: String? = null,
    /** The most recently created invitation code — shown prominently so the admin can share it. */
    val lastCreatedCode: String? = null,
    val deleting: Boolean = false,
    /** Set after a successful family deletion so the Composable can navigate away. */
    val deleted: Boolean = false,
) {
    /** Join requests awaiting admin approval. */
    val pendingRequests: List<Invitation>
        get() = invitations.filter { it.status == "requested" }

    /** Codes issued by admin but not yet used. */
    val pendingInvitations: List<Invitation>
        get() = invitations.filter { it.status == "pending" }
}

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val invitationRepository: InvitationRepository,
    private val categoryRepository: CategoryRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FamilyUiState())
    val state: StateFlow<FamilyUiState> = _state.asStateFlow()

    fun load(familyId: String) {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid.orEmpty()
            val role = familyRepository.currentUserRole(familyId, uid)
            val family = familyRepository.getFamily(familyId)
            _state.update {
                it.copy(
                    family = family,
                    isAdmin = role == Role.admin,
                    isCreator = family?.createdBy == uid,
                )
            }

            launch {
                familyRepository.observeMembers(familyId).collect { list ->
                    _state.update { it.copy(members = list) }
                    resolveProfiles(list.map { it.userId })
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

    private fun resolveProfiles(uids: List<String>) {
        val needed = uids.filter { it !in _state.value.profiles && it.isNotBlank() }
        if (needed.isEmpty()) return
        viewModelScope.launch {
            runCatching { userRepository.getByIds(needed) }
                .onSuccess { fetched ->
                    _state.update { it.copy(profiles = it.profiles + fetched) }
                }
        }
    }

    fun invite(familyId: String, email: String) {
        viewModelScope.launch {
            runCatching { invitationRepository.invite(familyId, email) }
                .onSuccess { code ->
                    _state.update {
                        it.copy(info = "Invitation code generated", lastCreatedCode = code, error = null)
                    }
                }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
        }
    }

    fun approveRequest(invitationId: String) {
        viewModelScope.launch {
            runCatching { invitationRepository.approve(invitationId) }
                .onSuccess { _state.update { it.copy(info = "Approved", error = null) } }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
        }
    }

    fun rejectRequest(invitationId: String) {
        viewModelScope.launch {
            runCatching { invitationRepository.reject(invitationId) }
                .onSuccess { _state.update { it.copy(info = "Rejected", error = null) } }
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
            runCatching { invitationRepository.delete(invitationId) }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
        }
    }

    /** Cascade-deletes the entire family. Only the creator should be able to invoke this. */
    fun deleteFamily(familyId: String) {
        val uid = authRepository.currentUser?.uid ?: return
        if (!_state.value.isCreator) return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true, error = null) }
            runCatching { familyRepository.deleteFamilyCascade(familyId, uid) }
                .onSuccess { _state.update { it.copy(deleting = false, deleted = true) } }
                .onFailure { err -> _state.update { it.copy(deleting = false, error = err.message) } }
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, info = null) }
}
