package com.timmat.financetracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timmat.financetracker.data.model.AppCurrency
import com.timmat.financetracker.data.model.Category
import com.timmat.financetracker.data.model.Family
import com.timmat.financetracker.data.model.Role
import com.timmat.financetracker.data.model.Transaction
import com.timmat.financetracker.data.model.TxType
import com.timmat.financetracker.data.repository.AuthRepository
import com.timmat.financetracker.data.repository.CategoryRepository
import com.timmat.financetracker.data.repository.FamilyRepository
import com.timmat.financetracker.data.repository.SettingsRepository
import com.timmat.financetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/** One data point on the 6-month history chart. */
data class MonthBar(
    val year: Int,
    val month: Int, // 0-based (Calendar.MONTH)
    val label: String, // short localised month label
    val income: Double,
    val expense: Double,
) { val remaining: Double get() = income - expense }

data class DashboardUiState(
    val family: Family? = null,
    val isAdmin: Boolean = false,
    val currencyCode: String = AppCurrency.EUR.code,
    val currentMonthLabel: String = "",
    val monthlyIncome: Double = 0.0,
    val monthlyExpense: Double = 0.0,
    val monthlyRemaining: Double = 0.0,
    val unpaidTotal: Double = 0.0,
    val incomeTransactions: List<Transaction> = emptyList(),
    val expenseTransactions: List<Transaction> = emptyList(),
    /** categoryId -> name lookup for rendering rows. */
    val categoryNames: Map<String, String> = emptyMap(),
    val history: List<MonthBar> = emptyList(),
    val chartExpanded: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
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

            // Fire-and-forget one-off cleanup (idempotent, gated by SharedPreferences).
            launch { runCleanupIfNewMonth(familyId) }

            // Live category map
            launch {
                categoryRepository.observe(familyId).collect { cats ->
                    _state.update { it.copy(categoryNames = cats.associate { c -> c.id to c.name }) }
                }
            }

            // Current month live data
            launch {
                transactionRepository.observeCurrentMonth(familyId).collect { txs ->
                    val income = txs.filter { it.typeEnum == TxType.income }
                    val expense = txs.filter { it.typeEnum == TxType.expense }
                    val incomeSum = income.sumOf { it.amount }
                    val expenseSum = expense.sumOf { it.amount }
                    val unpaidSum = expense.filter { !it.paid }.sumOf { it.amount }
                    _state.update {
                        it.copy(
                            loading = false,
                            monthlyIncome = incomeSum,
                            monthlyExpense = expenseSum,
                            monthlyRemaining = incomeSum - expenseSum,
                            unpaidTotal = unpaidSum,
                            incomeTransactions = income.sortedByDescending { tx -> tx.date.seconds },
                            expenseTransactions = expense.sortedByDescending { tx -> tx.date.seconds },
                            currentMonthLabel = currentMonthLabel(),
                            currencyCode = settingsRepository.currency.code,
                        )
                    }
                }
            }

            // 6-month history chart
            launch {
                transactionRepository.observeLastMonths(familyId, 6).collect { txs ->
                    _state.update { it.copy(history = buildHistory(txs)) }
                }
            }

            // React to currency changes
            launch {
                settingsRepository.observe().collect {
                    _state.update { it.copy(currencyCode = settingsRepository.currency.code) }
                }
            }
        }
    }

    fun togglePaid(transaction: Transaction, paid: Boolean) {
        if (transaction.id.isBlank()) return
        viewModelScope.launch {
            runCatching { transactionRepository.setPaid(transaction.id, paid) }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun uncheckAllPaid(familyId: String) {
        viewModelScope.launch {
            runCatching { transactionRepository.markAllExpensesUnpaidForCurrentMonth(familyId) }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun toggleChart() {
        _state.update { it.copy(chartExpanded = !it.chartExpanded) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun runCleanupIfNewMonth(familyId: String) {
        val key = currentMonthKey()
        if (settingsRepository.lastCleanupMonthKey == key) return
        viewModelScope.launch {
            runCatching { transactionRepository.cleanupOneOffsBeforeCurrentMonth(familyId) }
            settingsRepository.lastCleanupMonthKey = key
        }
    }

    private fun buildHistory(txs: List<Transaction>): List<MonthBar> {
        val buckets = linkedMapOf<String, MonthBar>()
        val cal = Calendar.getInstance()
        // Seed the last 6 month buckets so empty months still render.
        for (i in 5 downTo 0) {
            cal.time = java.util.Date()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, -i)
            val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH)
            buckets["$y-$m"] = MonthBar(y, m, monthShort(cal), 0.0, 0.0)
        }
        for (t in txs) {
            cal.time = t.date.toDate()
            val k = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
            val existing = buckets[k] ?: continue
            buckets[k] = when (t.typeEnum) {
                TxType.income -> existing.copy(income = existing.income + t.amount)
                TxType.expense -> existing.copy(expense = existing.expense + t.amount)
            }
        }
        return buckets.values.toList()
    }

    private fun monthShort(cal: Calendar): String =
        cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, java.util.Locale.getDefault()).orEmpty()

    private fun currentMonthLabel(): String {
        val cal = Calendar.getInstance()
        val month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale.getDefault())
            .orEmpty().replaceFirstChar { it.uppercase() }
        return "$month ${cal.get(Calendar.YEAR)}"
    }

    private fun currentMonthKey(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
    }
}
