package com.timmat.financetracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timmat.financetracker.data.model.Budget
import com.timmat.financetracker.data.model.Category
import com.timmat.financetracker.data.model.Family
import com.timmat.financetracker.data.model.Role
import com.timmat.financetracker.data.model.Transaction
import com.timmat.financetracker.data.model.TxType
import com.timmat.financetracker.data.repository.AuthRepository
import com.timmat.financetracker.data.repository.BudgetRepository
import com.timmat.financetracker.data.repository.CategoryRepository
import com.timmat.financetracker.data.repository.FamilyRepository
import com.timmat.financetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategorySpend(
    val category: Category,
    val spent: Double,
    val limit: Double?,
) {
    val remaining: Double? get() = limit?.let { it - spent }
    val progress: Float
        get() = limit?.let { if (it <= 0) 0f else (spent / it).toFloat().coerceIn(0f, 1f) } ?: 0f
}

data class DashboardUiState(
    val family: Family? = null,
    val isAdmin: Boolean = false,
    val monthlyIncome: Double = 0.0,
    val monthlyExpense: Double = 0.0,
    val perCategory: List<CategorySpend> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    fun load(familyId: String) {
        viewModelScope.launch {
            val user = authRepository.currentUser ?: run {
                _state.update { it.copy(loading = false, error = "Not signed in") }; return@launch
            }
            val role = familyRepository.currentUserRole(familyId, user.uid)
            val family = familyRepository.getFamily(familyId)
            _state.update { it.copy(family = family, isAdmin = role == Role.admin) }

            combine(
                transactionRepository.observeCurrentMonth(familyId),
                categoryRepository.observe(familyId),
                budgetRepository.observe(familyId),
            ) { txs, cats, budgets ->
                compute(txs, cats, budgets)
            }.collect { result ->
                _state.update { it.copy(loading = false, error = null,
                    monthlyIncome = result.first,
                    monthlyExpense = result.second,
                    perCategory = result.third) }
            }
        }
    }

    private fun compute(
        txs: List<Transaction>,
        cats: List<Category>,
        budgets: List<Budget>,
    ): Triple<Double, Double, List<CategorySpend>> {
        var income = 0.0
        var expense = 0.0
        val spentByCat = HashMap<String, Double>()
        for (t in txs) {
            when (t.typeEnum) {
                TxType.income -> income += t.amount
                TxType.expense -> {
                    expense += t.amount
                    spentByCat.merge(t.categoryId, t.amount, Double::plus)
                }
            }
        }
        val limitByCat = budgets.associateBy({ it.categoryId }, { it.monthlyLimit })
        val per = cats.map { cat ->
            CategorySpend(
                category = cat,
                spent = spentByCat[cat.id] ?: 0.0,
                limit = limitByCat[cat.id],
            )
        }.sortedByDescending { it.spent }
        return Triple(income, expense, per)
    }
}
