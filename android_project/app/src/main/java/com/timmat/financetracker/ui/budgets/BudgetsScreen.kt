package com.timmat.financetracker.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel

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
    val limitByCat = remember(state.budgets) { state.budgets.associateBy({ it.categoryId }, { it.monthlyLimit }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!state.isAdmin) {
                item {
                    Text(
                        "Only admins can edit monthly limits.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(state.categories, key = { it.id }) { cat ->
                val currentLimit = limitByCat[cat.id]
                val typed = editing[cat.id] ?: currentLimit?.toString() ?: ""
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(cat.name, style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = typed,
                                onValueChange = { new ->
                                    editing[cat.id] = new.filter { it.isDigit() || it == '.' }
                                },
                                enabled = state.isAdmin,
                                label = { Text("Monthly limit") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer8()
                            Button(
                                enabled = state.isAdmin && typed.toDoubleOrNull() != null,
                                onClick = {
                                    val limit = typed.toDoubleOrNull() ?: return@Button
                                    viewModel.upsert(familyId, cat.id, limit)
                                },
                            ) { Text("Save") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Spacer8() =
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
