package com.timmat.financetracker.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.timmat.financetracker.R
import com.timmat.financetracker.data.model.AppCurrency
import com.timmat.financetracker.data.model.AppLanguage
import com.timmat.financetracker.data.model.AppLockMode
import com.timmat.financetracker.data.model.AppTheme
import com.timmat.financetracker.ui.lock.AppLockSetupScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showAppLockSetup by remember { mutableStateOf(false) }

    if (showAppLockSetup) {
        AppLockSetupScreen(
            onDone = { showAppLockSetup = false },
            onBack = { showAppLockSetup = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Theme
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column {
                    AppTheme.values().forEachIndexed { idx, t ->
                        ListItem(
                            headlineContent = { Text(stringResource(t.displayNameRes)) },
                            trailingContent = {
                                RadioButton(selected = state.theme == t, onClick = { viewModel.setTheme(t) })
                            },
                            modifier = Modifier.clickable { viewModel.setTheme(t) },
                        )
                        if (idx != AppTheme.values().lastIndex) HorizontalDivider()
                    }
                }
            }

            // Language
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column {
                    AppLanguage.values().forEachIndexed { idx, lang ->
                        val select = {
                            viewModel.setLanguage(lang)
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
                        }
                        ListItem(
                            headlineContent = { Text(stringResource(lang.displayNameRes)) },
                            trailingContent = {
                                RadioButton(selected = state.language == lang, onClick = select)
                            },
                            modifier = Modifier.clickable { select() },
                        )
                        if (idx != AppLanguage.values().lastIndex) HorizontalDivider()
                    }
                }
            }

            // Currency
            Text(stringResource(R.string.settings_currency), style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column {
                    AppCurrency.values().forEachIndexed { idx, cur ->
                        ListItem(
                            headlineContent = { Text(stringResource(cur.displayNameRes)) },
                            trailingContent = {
                                RadioButton(selected = state.currency == cur, onClick = { viewModel.setCurrency(cur) })
                            },
                            modifier = Modifier.clickable { viewModel.setCurrency(cur) },
                        )
                        if (idx != AppCurrency.values().lastIndex) HorizontalDivider()
                    }
                }
            }

            // App lock
            Text(stringResource(R.string.settings_app_lock), style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Lock, null) },
                        headlineContent = { Text(stringResource(R.string.settings_app_lock_current)) },
                        supportingContent = { Text(stringResource(state.appLock.displayNameRes)) },
                    )
                    HorizontalDivider()
                    if (state.appLock == AppLockMode.None) {
                        TextButton(
                            onClick = { showAppLockSetup = true },
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                        ) { Text(stringResource(R.string.settings_enable_app_lock)) }
                    } else {
                        TextButton(
                            onClick = { showAppLockSetup = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        ) { Text(stringResource(R.string.settings_change_pin)) }
                        TextButton(
                            onClick = { viewModel.lockNow() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        ) { Text(stringResource(R.string.settings_lock_now)) }
                        TextButton(
                            onClick = { viewModel.disableAppLock() },
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.settings_disable_app_lock)) }
                    }
                }
            }

            Text(stringResource(R.string.settings_about_heading), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}
