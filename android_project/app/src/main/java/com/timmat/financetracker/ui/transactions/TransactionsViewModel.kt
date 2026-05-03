package com.timmat.financetracker.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timmat.financetracker.data.model.Category
import com.timmat.financetracker.data.model.Recurrence
import com.timmat.financetracker.data.model.Transaction
import com.timmat.financetracker.data.model.TxType
import com.timmat.financetracker.data.repository.AuthRepository
import com.timmat.financetracker.data.repository.CategoryRepository
import com.timmat.financetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val submitting: Boolean = false,
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionsUiState())
    val state: StateFlow<TransactionsUiState> = _state.asStateFlow()

    fun load(familyId: String) {
        viewModelScope.launch {
            launch {
                transactionRepository.observeForFamily(familyId).collect { list ->
                    _state.update { it.copy(transactions = list, loading = false) }
                }
            }
            launch {
                categoryRepository.observe(familyId).collect { list ->
                    _state.update { it.copy(categories = list) }
                }
            }
        }
    }

    fun add(
        familyId: String,
        amount: Double,
        type: TxType,
        categoryId: String,
        date: Date,
        recurrence: Recurrence,
        onDone: () -> Unit,
    ) {
        val user = authRepository.currentUser ?: return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            runCatching {
                transactionRepository.add(
                    familyId = familyId,
                    userId = user.uid,
                    amount = amount,
                    type = type,
                    categoryId = categoryId,
                    date = date,
                    recurrence = recurrence,
                )
            }.onSuccess {
                _state.update { it.copy(submitting = false) }
                onDone()
            }.onFailure { err ->
                _state.update { it.copy(submitting = false, error = err.message) }
            }
        }
    }

    fun delete(txId: String) {
        viewModelScope.launch {
            runCatching { transactionRepository.delete(txId) }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
        }
    }
}
