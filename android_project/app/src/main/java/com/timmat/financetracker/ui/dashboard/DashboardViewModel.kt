package com.timmat.financetracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timmat.financetracker.common.currentBillingCycle
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

data class MonthBar(
    val year: Int,
    val month: Int,
    val label: String,
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
    val categoryNames: Map<String, String> = emptyMap(),
    val history: List<MonthBar> = emptyList(),
    val chartExpanded: Boolean = false,
    val incomeExpanded: Boolean = true,
    val expensesExpanded: Boolean = true,
    val searchQuery: String = "",
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

    private var currentFamilyId: String = ""
    private var monthStartDay: Int = 1

    fun load(familyId: String) {
        currentFamilyId = familyId
        viewModelScope.launch {
            val user = authRepository.currentUser ?: run {
                _state.update { it.copy(loading = false, error = "Not signed in") }; return@launch
            }
            val role = familyRepository.currentUserRole(familyId, user.uid)
            // Fetch family ONCE first so we know the cycle-start day before any listeners fire.
            val initialFamily = familyRepository.getFamily(familyId)
            monthStartDay = initialFamily?.monthStartDay ?: 1
            _state.update {
                it.copy(
                    isAdmin = role == Role.admin,
                    family = initialFamily,
                    incomeExpanded = settingsRepository.incomeExpanded,
                    expensesExpanded = settingsRepository.expensesExpanded,
                )
            }
            initialFamily?.let { runAutoResetIfNeeded(it) }

            // Keep family doc live for downstream reads (rule & role already loaded).
            launch {
                familyRepository.observeFamily(familyId).collect { family ->
                    if (family != null) {
                        _state.update { it.copy(family = family) }
                        // If the admin changed the cycle day, reflect it locally for next session.
                        monthStartDay = family.monthStartDay
                        runAutoResetIfNeeded(family)
                    }
                }
            }

            launch { runCleanupIfNewMonth(familyId) }

            launch {
                categoryRepository.observe(familyId).collect { cats ->
                    _state.update { it.copy(categoryNames = cats.associate { c -> c.id to c.name }) }
                    // also re-sort existing lists against latest names
                    _state.update { resortLists(it) }
                }
            }

            launch {
                transactionRepository.observeCurrentMonth(familyId, monthStartDay).collect { txs ->
                    val income = txs.filter { it.typeEnum == TxType.income }
                    val expense = txs.filter { it.typeEnum == TxType.expense }
                    val incomeSum = income.sumOf { it.amount }
                    val expenseSum = expense.sumOf { it.amount }
                    val unpaidSum = expense.filter { !it.paid }.sumOf { it.amount }
                    _state.update {
                        resortLists(
                            it.copy(
                                loading = false,
                                monthlyIncome = incomeSum,
                                monthlyExpense = expenseSum,
                                monthlyRemaining = incomeSum - expenseSum,
                                unpaidTotal = unpaidSum,
                                incomeTransactions = income,
                                expenseTransactions = expense,
                                currentMonthLabel = currentMonthLabel(),
                                currencyCode = settingsRepository.currency.code,
                            )
                        )
                    }
                }
            }

            launch {
                transactionRepository.observeLastMonths(familyId, 6, monthStartDay).collect { txs ->
                    _state.update { it.copy(history = buildHistory(txs)) }
                }
            }

            launch {
                settingsRepository.observe().collect {
                    _state.update { it.copy(currencyCode = settingsRepository.currency.code) }
                }
            }
        }
    }

    private fun resortLists(s: DashboardUiState): DashboardUiState {
        val names = s.categoryNames
        fun sortKey(tx: Transaction): String =
            (names[tx.categoryId] ?: "zzz").lowercase()
        return s.copy(
            incomeTransactions = s.incomeTransactions.sortedBy(::sortKey),
            expenseTransactions = s.expenseTransactions.sortedBy(::sortKey),
        )
    }

    private fun runAutoResetIfNeeded(family: Family) {
        if (!family.autoResetPaidOnRollover) return
        val cycle = currentBillingCycle(family.monthStartDay)
        val stored = settingsRepository.lastResetCycleKey(family.id)
        if (stored.isEmpty()) {
            settingsRepository.setLastResetCycleKey(family.id, cycle.key)
            return
        }
        if (stored != cycle.key) {
            viewModelScope.launch {
                runCatching {
                    transactionRepository.markAllExpensesUnpaidForCurrentMonth(family.id, family.monthStartDay)
                }
                settingsRepository.setLastResetCycleKey(family.id, cycle.key)
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
            runCatching { transactionRepository.markAllExpensesUnpaidForCurrentMonth(familyId, monthStartDay) }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun deleteTransaction(txId: String) {
        viewModelScope.launch {
            runCatching { transactionRepository.delete(txId) }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun toggleChart() {
        _state.update { it.copy(chartExpanded = !it.chartExpanded) }
    }

    fun toggleIncome() {
        val new = !_state.value.incomeExpanded
        settingsRepository.incomeExpanded = new
        _state.update { it.copy(incomeExpanded = new) }
    }

    fun toggleExpenses() {
        val new = !_state.value.expensesExpanded
        settingsRepository.expensesExpanded = new
        _state.update { it.copy(expensesExpanded = new) }
    }

    fun setSearchQuery(q: String) { _state.update { it.copy(searchQuery = q) } }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun runCleanupIfNewMonth(familyId: String) {
        val key = currentMonthKey()
        if (settingsRepository.lastCleanupMonthKey == key) return
        viewModelScope.launch {
            runCatching { transactionRepository.cleanupOneOffsBeforeCurrentMonth(familyId, monthStartDay) }
            settingsRepository.lastCleanupMonthKey = key
        }
    }

    private fun buildHistory(txs: List<Transaction>): List<MonthBar> {
        val buckets = linkedMapOf<String, MonthBar>()
        val cal = Calendar.getInstance()
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
