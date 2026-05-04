package com.timmat.financetracker.ui.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.timmat.financetracker.R
import com.timmat.financetracker.data.model.Recurrence
import com.timmat.financetracker.data.model.TxType
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    familyId: String,
    editingTxId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(familyId) { viewModel.load(familyId) }
    LaunchedEffect(editingTxId) {
        if (!editingTxId.isNullOrBlank()) viewModel.loadForEdit(editingTxId)
    }

    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TxType.expense) }
    var recurrence by remember { mutableStateOf(Recurrence.none) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }

    // Populate fields once the editing tx loads.
    LaunchedEffect(state.editing) {
        state.editing?.let { tx ->
            if (!initialized) {
                amount = (if (tx.amount % 1.0 == 0.0) tx.amount.toLong().toString() else tx.amount.toString())
                type = tx.typeEnum
                recurrence = tx.recurrenceEnum
                categoryId = tx.categoryId
                initialized = true
            }
        }
    }

    LaunchedEffect(state.categories) {
        if (categoryId == null && state.categories.isNotEmpty()) {
            categoryId = state.categories.first().id
        }
    }

    val selectedCategoryName = state.categories.firstOrNull { it.id == categoryId }?.name ?: ""
    val amountValid = amount.replace(',', '.').toDoubleOrNull()?.let { it > 0 } == true
    val canSubmit = amountValid && !categoryId.isNullOrBlank() && !state.submitting
    val isEditMode = !editingTxId.isNullOrBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isEditMode) R.string.tx_edit_title else R.string.tx_add_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == TxType.expense,
                    onClick = { type = TxType.expense },
                    label = { Text(stringResource(R.string.tx_expense)) },
                )
                FilterChip(
                    selected = type == TxType.income,
                    onClick = { type = TxType.income },
                    label = { Text(stringResource(R.string.tx_income)) },
                )
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { new -> amount = new.filter { it.isDigit() || it == '.' || it == ',' } },
                label = { Text(stringResource(R.string.tx_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            ExposedDropdownMenuBox(
                expanded = categoryMenuOpen,
                onExpandedChange = { categoryMenuOpen = !categoryMenuOpen },
            ) {
                OutlinedTextField(
                    value = selectedCategoryName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.tx_category)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryMenuOpen) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                    state.categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = { categoryId = cat.id; categoryMenuOpen = false },
                        )
                    }
                }
            }
            Text(stringResource(R.string.tx_recurrence), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = recurrence == Recurrence.none,
                    onClick = { recurrence = Recurrence.none },
                    label = { Text(stringResource(R.string.tx_recurrence_none)) },
                )
                FilterChip(
                    selected = recurrence == Recurrence.monthly,
                    onClick = { recurrence = Recurrence.monthly },
                    label = { Text(stringResource(R.string.tx_recurrence_monthly)) },
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = canSubmit,
                onClick = {
                    val parsed = amount.replace(',', '.').toDouble()
                    if (isEditMode) {
                        viewModel.update(
                            txId = editingTxId!!,
                            amount = parsed,
                            type = type,
                            categoryId = categoryId!!,
                            date = state.editing?.date?.toDate() ?: Date(),
                            recurrence = recurrence,
                            onDone = onSaved,
                        )
                    } else {
                        viewModel.add(
                            familyId = familyId,
                            amount = parsed,
                            type = type,
                            categoryId = categoryId!!,
                            date = Date(),
                            recurrence = recurrence,
                            onDone = onSaved,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.submitting) stringResource(R.string.tx_saving) else stringResource(R.string.action_save))
            }
            state.error?.let { err -> Text(err, color = MaterialTheme.colorScheme.error) }
        }
    }
}
