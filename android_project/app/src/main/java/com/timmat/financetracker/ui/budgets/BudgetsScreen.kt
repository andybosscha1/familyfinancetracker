package com.timmat.financetracker.ui.budgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.timmat.financetracker.R
import com.timmat.financetracker.common.currencyFormatter
import com.timmat.financetracker.data.model.Budget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    familyId: String,
    onBack: () -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(familyId) { viewModel.load(familyId) }

    val editing = remember { mutableStateMapOf<String, String>() }
    val budgetByCat: Map<String, Budget> = remember(state.budgets) {
        state.budgets.associateBy { it.categoryId }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val money = remember(state.currency) { currencyFormatter(state.currency) }

    // Confirmation dialog state for deletion
    var pendingDelete by remember { mutableStateOf<Budget?>(null) }

    val savedMessage = stringResource(R.string.budgets_saved)
    val removedMessage = stringResource(R.string.budgets_removed)

    // Surface save / delete toasts.
    LaunchedEffect(state.toast) {
        when (state.toast) {
            BudgetsToast.Saved -> {
                snackbarHostState.showSnackbar(savedMessage)
                viewModel.clearToast()
            }
            BudgetsToast.Removed -> {
                snackbarHostState.showSnackbar(removedMessage)
                viewModel.clearToast()
            }
            null -> Unit
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.budgets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!state.isAdmin) {
                item {
                    Text(
                        stringResource(R.string.budgets_not_admin),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(state.categories, key = { it.id }) { cat ->
                val existing = budgetByCat[cat.id]
                val current = existing?.monthlyLimit
                val typed = editing[cat.id] ?: current?.let { formatPlain(it) } ?: ""
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(cat.name, style = MaterialTheme.typography.titleMedium)
                                if (current != null) {
                                    Text(
                                        money.format(current),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    )
                                }
                            }
                            if (state.isAdmin && existing != null) {
                                IconButton(onClick = { pendingDelete = existing }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = typed,
                                onValueChange = { new ->
                                    editing[cat.id] = new.filter { it.isDigit() || it == '.' || it == ',' }
                                },
                                enabled = state.isAdmin,
                                label = { Text(stringResource(R.string.budgets_monthly_limit)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            val parsed = typed.replace(',', '.').toDoubleOrNull()
                            Button(
                                enabled = state.isAdmin && parsed != null && parsed >= 0.0,
                                onClick = {
                                    val limit = parsed ?: return@Button
                                    viewModel.upsert(familyId, cat.id, limit)
                                    editing.remove(cat.id)
                                },
                            ) { Text(stringResource(R.string.action_save)) }
                        }
                    }
                }
            }
        }

        pendingDelete?.let { budget ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.budgets_title)) },
                text = { Text(stringResource(R.string.budgets_remove_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.delete(budget.id)
                        pendingDelete = null
                    }) { Text(stringResource(R.string.action_remove)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

private fun formatPlain(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
