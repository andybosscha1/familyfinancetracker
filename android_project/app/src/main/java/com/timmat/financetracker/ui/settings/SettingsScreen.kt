package com.timmat.financetracker.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Language
            Text(stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium)
            Column {
                AppLanguage.values().forEach { lang ->
                    ListItem(
                        headlineContent = { Text(stringResource(lang.displayNameRes)) },
                        trailingContent = {
                            RadioButton(
                                selected = state.language == lang,
                                onClick = {
                                    viewModel.setLanguage(lang)
                                    AppCompatDelegate.setApplicationLocales(
                                        LocaleListCompat.forLanguageTags(lang.tag)
                                    )
                                },
                            )
                        },
                        modifier = Modifier.clickableItem {
                            viewModel.setLanguage(lang)
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(lang.tag)
                            )
                        },
                    )
                    HorizontalDivider()
                }
            }

            // Currency
            Text(stringResource(R.string.settings_currency),
                style = MaterialTheme.typography.titleMedium)
            Column {
                AppCurrency.values().forEach { cur ->
                    ListItem(
                        headlineContent = { Text(stringResource(cur.displayNameRes)) },
                        trailingContent = {
                            RadioButton(
                                selected = state.currency == cur,
                                onClick = { viewModel.setCurrency(cur) },
                            )
                        },
                        modifier = Modifier.clickableItem { viewModel.setCurrency(cur) },
                    )
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.settings_about_heading),
                style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

private fun Modifier.clickableItem(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable { onClick() })

// Tiny shim so we don't have to import `foundation.clickable` everywhere.
private inline fun androidx.compose.foundation.clickable(crossinline onClick: () -> Unit) =
    androidx.compose.ui.Modifier.composed {
        androidx.compose.foundation.clickable(
            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
            indication = androidx.compose.material.ripple.rememberRipple(),
        ) { onClick() }
    }
