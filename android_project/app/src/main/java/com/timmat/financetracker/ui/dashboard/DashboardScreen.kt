package com.timmat.financetracker.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    familyId: String,
    onOpenTransactions: () -> Unit,
    onAddTransaction: () -> Unit,
    onEditTransaction: (String) -> Unit,
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
    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val q = state.searchQuery.trim().lowercase()
    val filteredIncome = if (q.isEmpty()) state.incomeTransactions else
        state.incomeTransactions.filter { txMatches(it, state.categoryNames, q) }
    val filteredExpenses = if (q.isEmpty()) state.expenseTransactions else
        state.expenseTransactions.filter { txMatches(it, state.categoryNames, q) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(24.dp))
                Text(
                    state.family?.name ?: stringResource(R.string.nav_dashboard),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(16.dp))
                if (state.isAdmin) {
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.nav_family)) },
                        selected = false,
                        onClick = { scope.launch { drawerState.close() }; onOpenFamily() },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_budgets)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onOpenBudgets() },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_settings)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onOpenSettings() },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.dashboard_view_all_history)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onOpenTransactions() },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.action_sign_out)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onSignOut() },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
                        }
                    },
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
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SummaryCard(Modifier.weight(1f), stringResource(R.string.dashboard_income), money.format(state.monthlyIncome), Income)
                        SummaryCard(Modifier.weight(1f), stringResource(R.string.dashboard_expenses), money.format(state.monthlyExpense), Expense)
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SummaryCard(Modifier.weight(1f), stringResource(R.string.dashboard_unpaid), money.format(state.unpaidTotal), if (state.unpaidTotal > 0) Expense else Income)
                        SummaryCard(Modifier.weight(1f), stringResource(R.string.dashboard_remaining), money.format(state.monthlyRemaining), if (state.monthlyRemaining >= 0) Income else Expense)
                    }
                }

                // Search bar
                item {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        placeholder = { Text(stringResource(R.string.dashboard_search_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Chart collapsible
                if (state.history.isNotEmpty()) {
                    item {
                        OutlinedButton(
                            onClick = { viewModel.toggleChart() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(if (state.chartExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (state.chartExpanded) R.string.dashboard_hide_chart else R.string.dashboard_show_chart))
                        }
                        AnimatedVisibility(state.chartExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(stringResource(R.string.dashboard_six_month_chart), style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(8.dp))
                                    MonthlyChart(bars = state.history)
                                }
                            }
                        }
                    }
                }

                // INCOME
                item {
                    CollapsibleHeader(
                        title = stringResource(R.string.dashboard_income),
                        expanded = state.incomeExpanded,
                        color = Income,
                        onToggle = { viewModel.toggleIncome() },
                    )
                }
                if (state.incomeExpanded) {
                    if (filteredIncome.isEmpty()) {
                        item { EmptyRow(stringResource(R.string.dashboard_no_income)) }
                    } else {
                        items(filteredIncome, key = { "in_${it.id}" }) { tx ->
                            TxRow(
                                tx = tx,
                                money = money,
                                categoryName = state.categoryNames[tx.categoryId],
                                isAdmin = state.isAdmin,
                                isExpense = false,
                                onPaidChange = null,
                                onEdit = { onEditTransaction(tx.id) },
                                onDelete = { pendingDelete = tx },
                            )
                        }
                    }
                }

                // EXPENSES header with uncheck-all
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        CollapsibleHeader(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.dashboard_expenses),
                            expanded = state.expensesExpanded,
                            color = Expense,
                            onToggle = { viewModel.toggleExpenses() },
                        )
                        if (state.expenseTransactions.any { it.paid }) {
                            TextButton(onClick = { pendingUncheckAll = true }) {
                                Text(stringResource(R.string.dashboard_uncheck_all))
                            }
                        }
                    }
                }
                if (state.expensesExpanded) {
                    if (filteredExpenses.isEmpty()) {
                        item { EmptyRow(stringResource(R.string.dashboard_no_expenses)) }
                    } else {
                        items(filteredExpenses, key = { "ex_${it.id}" }) { tx ->
                            TxRow(
                                tx = tx,
                                money = money,
                                categoryName = state.categoryNames[tx.categoryId],
                                isAdmin = state.isAdmin,
                                isExpense = true,
                                onPaidChange = { viewModel.togglePaid(tx, it) },
                                onEdit = { onEditTransaction(tx.id) },
                                onDelete = { pendingDelete = tx },
                            )
                        }
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

            pendingDelete?.let { tx ->
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    title = { Text(stringResource(R.string.tx_delete_title)) },
                    text = { Text(stringResource(R.string.tx_delete_confirm)) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteTransaction(tx.id)
                            pendingDelete = null
                        }) { Text(stringResource(R.string.action_delete)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
                    },
                )
            }
        }
    }
}

private fun txMatches(tx: Transaction, categoryNames: Map<String, String>, q: String): Boolean {
    val name = categoryNames[tx.categoryId].orEmpty().lowercase()
    val amount = tx.amount.toString()
    return name.contains(q) || amount.contains(q)
}

@Composable
private fun CollapsibleHeader(
    title: String,
    expanded: Boolean,
    color: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = color,
        )
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun SummaryCard(modifier: Modifier, title: String, value: String, color: Color) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
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
private fun TxRow(
    tx: Transaction,
    money: NumberFormat,
    categoryName: String?,
    isAdmin: Boolean,
    isExpense: Boolean,
    onPaidChange: ((Boolean) -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(categoryName ?: "—", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(formatDate(tx), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            val amountColor = when {
                isExpense && tx.paid -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                isExpense -> Expense
                else -> Income
            }
            Text(money.format(tx.amount), style = MaterialTheme.typography.titleMedium, color = amountColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 4.dp))
            if (isExpense && onPaidChange != null) {
                Checkbox(checked = tx.paid, onCheckedChange = onPaidChange)
            }
            if (isAdmin) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            text = { Text(stringResource(R.string.action_edit)) },
                            onClick = { menuOpen = false; onEdit() },
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            text = { Text(stringResource(R.string.action_delete)) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

private val dateFormatter by lazy { SimpleDateFormat("d MMM", Locale.getDefault()) }
private fun formatDate(tx: Transaction): String =
    runCatching { dateFormatter.format(tx.date.toDate()) }.getOrDefault("")
