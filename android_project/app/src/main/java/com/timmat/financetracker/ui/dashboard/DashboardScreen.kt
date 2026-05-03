package com.timmat.financetracker.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.timmat.financetracker.R
import com.timmat.financetracker.common.currencyFormatter
import com.timmat.financetracker.data.model.AppCurrency
import com.timmat.financetracker.ui.components.MonthlyChart
import com.timmat.financetracker.ui.theme.Expense
import com.timmat.financetracker.ui.theme.Income

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    familyId: String,
    onOpenTransactions: () -> Unit,
    onAddTransaction: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenFamily: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(familyId) { viewModel.load(familyId) }
    val money = remember(state.currencyCode) {
        currencyFormatter(AppCurrency.fromCode(state.currencyCode))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.family?.name ?: stringResource(R.string.nav_dashboard))
                        if (state.currentMonthLabel.isNotEmpty()) {
                            Text(
                                state.currentMonthLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                },
                actions = {
                    if (state.isAdmin) {
                        TextButton(onClick = onOpenFamily) {
                            Text(stringResource(R.string.nav_family))
                        }
                    }
                    TextButton(onClick = onOpenBudgets) { Text(stringResource(R.string.nav_budgets)) }
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.nav_settings)) }
                    TextButton(onClick = onSignOut) { Text(stringResource(R.string.action_sign_out)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTransaction,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.action_add)) },
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
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCard(
                title = stringResource(R.string.dashboard_income),
                value = money.format(state.monthlyIncome),
                color = Income,
            )
            SummaryCard(
                title = stringResource(R.string.dashboard_expenses),
                value = money.format(state.monthlyExpense),
                color = Expense,
            )
            SummaryCard(
                title = stringResource(R.string.dashboard_remaining),
                value = money.format(state.monthlyRemaining),
                color = if (state.monthlyRemaining >= 0) Income else Expense,
            )

            if (state.history.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.dashboard_six_month_chart),
                    style = MaterialTheme.typography.titleMedium,
                )
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        MonthlyChart(bars = state.history)
                    }
                }
            }

            TextButton(onClick = onOpenTransactions, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.dashboard_view_transactions))
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, color: Color) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
        }
    }
}
