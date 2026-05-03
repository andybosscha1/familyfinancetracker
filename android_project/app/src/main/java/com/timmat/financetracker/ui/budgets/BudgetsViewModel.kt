package com.timmat.financetracker.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timmat.financetracker.data.model.Budget
import com.timmat.financetracker.data.model.Category
import com.timmat.financetracker.data.model.Role
import com.timmat.financetracker.data.repository.AuthRepository
import com.timmat.financetracker.data.repository.BudgetRepository
import com.timmat.financetracker.data.repository.CategoryRepository
import com.timmat.financetracker.data.repository.FamilyRepository
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
    val error: String? = null,
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BudgetsUiState())
    val state: StateFlow<BudgetsUiState> = _state.asStateFlow()

    fun load(familyId: String) {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid.orEmpty()
            val role = familyRepository.currentUserRole(familyId, uid)
            _state.update { it.copy(isAdmin = role == Role.admin) }
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
        }
    }

    fun upsert(familyId: String, categoryId: String, monthlyLimit: Double) {
        viewModelScope.launch {
            runCatching { budgetRepository.upsert(familyId, categoryId, monthlyLimit) }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
        }
    }
}
