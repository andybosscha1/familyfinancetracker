package com.timmat.financetracker.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timmat.financetracker.data.model.AppCurrency
import com.timmat.financetracker.data.model.Budget
import com.timmat.financetracker.data.model.Category
import com.timmat.financetracker.data.model.Role
import com.timmat.financetracker.data.repository.AuthRepository
import com.timmat.financetracker.data.repository.BudgetRepository
import com.timmat.financetracker.data.repository.CategoryRepository
import com.timmat.financetracker.data.repository.FamilyRepository
import com.timmat.financetracker.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetsUiState(
    val categories: List<Category> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val isAdmin: Boolean = false,
    val currency: AppCurrency = AppCurrency.EUR,
    val error: String? = null,
    /** Transient event key — each save/delete increments this so the UI can show a snackbar. */
    val toast: BudgetsToast? = null,
)

enum class BudgetsToast { Saved, Removed }

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BudgetsUiState())
    val state: StateFlow<BudgetsUiState> = _state.asStateFlow()

    fun load(familyId: String) {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid.orEmpty()
            val role = familyRepository.currentUserRole(familyId, uid)
            _state.update {
                it.copy(
                    isAdmin = role == Role.admin,
                    currency = settingsRepository.currency,
                )
            }
            launch {
                categoryRepository.observe(familyId).collect { list ->
                    _state.update { it.copy(categories = list) }
                }
            }
            launch {
                budgetRepository.observe(familyId).collect { list ->
                    _state.update { it.copy(budgets = list) }
                }
            }
            launch {
                settingsRepository.observe().collect {
                    _state.update { it.copy(currency = settingsRepository.currency) }
                }
            }
        }
    }

    fun upsert(familyId: String, categoryId: String, monthlyLimit: Double) {
        viewModelScope.launch {
            runCatching { budgetRepository.upsert(familyId, categoryId, monthlyLimit) }
                .onSuccess { _state.update { it.copy(toast = BudgetsToast.Saved, error = null) } }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
        }
    }

    fun delete(budgetId: String) {
        if (budgetId.isBlank()) return
        viewModelScope.launch {
            runCatching { budgetRepository.delete(budgetId) }
                .onSuccess { _state.update { it.copy(toast = BudgetsToast.Removed, error = null) } }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
        }
    }

    fun clearToast() = _state.update { it.copy(toast = null) }
    fun clearError() = _state.update { it.copy(error = null) }
}
