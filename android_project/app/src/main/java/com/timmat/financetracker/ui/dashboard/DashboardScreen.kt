package com.timmat.financetracker.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.timmat.financetracker.ui.theme.Expense
import com.timmat.financetracker.ui.theme.Income
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    familyId: String,
    onOpenTransactions: () -> Unit,
    onAddTransaction: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenFamily: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(familyId) { viewModel.load(familyId) }
    val money = androidx.compose.runtime.remember { NumberFormat.getCurrencyInstance() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.family?.name ?: "Dashboard") },
                actions = {
                    if (state.isAdmin) {
                        TextButton(onClick = onOpenFamily) { Text("Family") }
                    }
                    TextButton(onClick = onOpenBudgets) { Text("Budgets") }
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTransaction,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("Income", money.format(state.monthlyIncome), Income, Modifier.weight(1f))
                SummaryCard("Expenses", money.format(state.monthlyExpense), Expense, Modifier.weight(1f))
            }

            Text("Budget usage", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.perCategory, key = { it.category.id }) { item ->
                    CategoryRow(item, money)
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenTransactions, modifier = Modifier.fillMaxWidth()) {
                Text("View all transactions")
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        }
    }
}

@Composable
private fun CategoryRow(item: CategorySpend, money: NumberFormat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.category.name, style = MaterialTheme.typography.titleMedium)
                val trailing = when (val rem = item.remaining) {
                    null -> money.format(item.spent)
                    else -> "${money.format(item.spent)} / ${money.format(item.limit ?: 0.0)}"
                }
                Text(trailing, style = MaterialTheme.typography.bodyMedium)
            }
            if (item.limit != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(2.dp))
                val remaining = item.remaining ?: 0.0
                Text(
                    text = if (remaining >= 0) "${money.format(remaining)} remaining"
                    else "Over by ${money.format(-remaining)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (remaining >= 0) Income else Expense,
                )
            }
        }
    }
}


