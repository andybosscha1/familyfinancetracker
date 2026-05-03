package com.timmat.financetracker.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.timmat.financetracker.R
import com.timmat.financetracker.common.currencyFormatter
import com.timmat.financetracker.data.model.AppCurrency
import com.timmat.financetracker.data.model.Transaction
import com.timmat.financetracker.ui.components.MonthlyChart
import com.timmat.financetracker.ui.theme.Expense
import com.timmat.financetracker.ui.theme.Income
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

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
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingUncheckAll by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            // ---------- Summary cards row ----------
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.dashboard_income),
                        value = money.format(state.monthlyIncome),
                        color = Income,
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.dashboard_expenses),
                        value = money.format(state.monthlyExpense),
                        color = Expense,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.dashboard_unpaid),
                        value = money.format(state.unpaidTotal),
                        color = if (state.unpaidTotal > 0) Expense else Income,
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.dashboard_remaining),
                        value = money.format(state.monthlyRemaining),
                        color = if (state.monthlyRemaining >= 0) Income else Expense,
                    )
                }
            }

            // ---------- Collapsible chart ----------
            if (state.history.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.toggleChart() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            if (state.chartExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.chartExpanded)
                                stringResource(R.string.dashboard_hide_chart)
                            else
                                stringResource(R.string.dashboard_show_chart)
                        )
                    }
                    AnimatedVisibility(
                        visible = state.chartExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    stringResource(R.string.dashboard_six_month_chart),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(8.dp))
                                MonthlyChart(bars = state.history)
                            }
                        }
                    }
                }
            }

            // ---------- Income section ----------
            item {
                SectionHeader(
                    title = stringResource(R.string.dashboard_income),
                    color = Income,
                )
            }
            if (state.incomeTransactions.isEmpty()) {
                item { EmptyRow(stringResource(R.string.dashboard_no_income)) }
            } else {
                items(state.incomeTransactions, key = { "in_${it.id}" }) { tx ->
                    IncomeRow(tx = tx, money = money, categoryName = state.categoryNames[tx.categoryId])
                }
            }

            // ---------- Expense section with paid checkboxes ----------
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SectionHeader(
                        title = stringResource(R.string.dashboard_expenses),
                        color = Expense,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.expenseTransactions.any { it.paid }) {
                        TextButton(onClick = { pendingUncheckAll = true }) {
                            Text(stringResource(R.string.dashboard_uncheck_all))
                        }
                    }
                }
            }
            if (state.expenseTransactions.isEmpty()) {
                item { EmptyRow(stringResource(R.string.dashboard_no_expenses)) }
            } else {
                items(state.expenseTransactions, key = { "ex_${it.id}" }) { tx ->
                    ExpenseRow(
                        tx = tx,
                        money = money,
                        categoryName = state.categoryNames[tx.categoryId],
                        onPaidChange = { viewModel.togglePaid(tx, it) },
                    )
                }
            }

            item {
                TextButton(onClick = onOpenTransactions, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dashboard_view_all_history))
                }
            }
        }

        if (pendingUncheckAll) {
            AlertDialog(
                onDismissRequest = { pendingUncheckAll = false },
                title = { Text(stringResource(R.string.dashboard_uncheck_all)) },
                text = { Text(stringResource(R.string.dashboard_uncheck_all_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingUncheckAll = false
                        viewModel.uncheckAllPaid(familyId)
                    }) { Text(stringResource(R.string.action_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingUncheckAll = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = modifier,
    )
}

@Composable
private fun EmptyRow(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun IncomeRow(tx: Transaction, money: NumberFormat, categoryName: String?) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    categoryName ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    formatDate(tx),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Text(
                money.format(tx.amount),
                style = MaterialTheme.typography.titleMedium,
                color = Income,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ExpenseRow(
    tx: Transaction,
    money: NumberFormat,
    categoryName: String?,
    onPaidChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    categoryName ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    formatDate(tx),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Text(
                money.format(tx.amount),
                style = MaterialTheme.typography.titleMedium,
                color = if (tx.paid) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else Expense,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 4.dp),
            )
            Checkbox(
                checked = tx.paid,
                onCheckedChange = onPaidChange,
            )
        }
    }
}

private val dateFormatter by lazy { SimpleDateFormat("d MMM", Locale.getDefault()) }
private fun formatDate(tx: Transaction): String =
    runCatching { dateFormatter.format(tx.date.toDate()) }.getOrDefault("")
