package com.timmat.financetracker.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.timmat.financetracker.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val user: FirebaseUser? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<AuthUiState> =
        combine(authRepository.authStateFlow(), loading, error) { user, isLoading, err ->
            AuthUiState(user = user, loading = isLoading, error = err)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AuthUiState(user = authRepository.currentUser),
        )

    fun signIn(context: Context) {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            runCatching { authRepository.signInWithGoogle(context) }
                .onFailure { error.value = it.message ?: "Sign-in failed" }
            loading.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    fun clearError() { error.value = null }
}
