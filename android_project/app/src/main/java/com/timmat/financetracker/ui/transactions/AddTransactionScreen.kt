package com.timmat.financetracker.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.timmat.financetracker.data.model.Recurrence
import com.timmat.financetracker.data.model.TxType
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    familyId: String,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(familyId) { viewModel.load(familyId) }

    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TxType.expense) }
    var recurrence by remember { mutableStateOf(Recurrence.none) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    // Default to the first category once categories arrive.
    LaunchedEffect(state.categories) {
        if (categoryId == null && state.categories.isNotEmpty()) {
            categoryId = state.categories.first().id
        }
    }

    val selectedCategoryName = state.categories.firstOrNull { it.id == categoryId }?.name ?: ""
    val amountValid = amount.toDoubleOrNull()?.let { it > 0 } == true
    val canSubmit = amountValid && !categoryId.isNullOrBlank() && !state.submitting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == TxType.expense,
                    onClick = { type = TxType.expense },
                    label = { Text("Expense") },
                )
                FilterChip(
                    selected = type == TxType.income,
                    onClick = { type = TxType.income },
                    label = { Text("Income") },
                )
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { new -> amount = new.filter { it.isDigit() || it == '.' } },
                label = { Text("Amount") },
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
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryMenuOpen) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                androidx.compose.material3.ExposedDropdownMenu(
                    expanded = categoryMenuOpen,
                    onDismissRequest = { categoryMenuOpen = false },
                ) {
                    state.categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                categoryId = cat.id
                                categoryMenuOpen = false
                            },
                        )
                    }
                }
            }

            Text("Recurrence", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = recurrence == Recurrence.none,
                    onClick = { recurrence = Recurrence.none },
                    label = { Text("One-off") },
                )
                FilterChip(
                    selected = recurrence == Recurrence.monthly,
                    onClick = { recurrence = Recurrence.monthly },
                    label = { Text("Monthly") },
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                enabled = canSubmit,
                onClick = {
                    viewModel.add(
                        familyId = familyId,
                        amount = amount.toDouble(),
                        type = type,
                        categoryId = categoryId!!,
                        date = Date(),
                        recurrence = recurrence,
                        onDone = onSaved,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.submitting) "Saving…" else "Save")
            }

            state.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
