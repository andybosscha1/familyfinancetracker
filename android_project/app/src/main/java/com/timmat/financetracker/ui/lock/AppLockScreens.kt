package com.timmat.financetracker.ui.lock

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.timmat.financetracker.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSetupScreen(
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
    isPromptAfterLogin: Boolean = false,
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    LaunchedEffect(Unit) { viewModel.refresh(activity) }
    val state by viewModel.state.collectAsState()

    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var useBiometric by remember { mutableStateOf(false) }

    val pinValid = pin.length in 4..6 && pin.all { it.isDigit() }
    val match = pin == confirmPin && pinValid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_lock_setup_title)) },
                navigationIcon = onBack?.let {
                    {
                        IconButton(onClick = it) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                } ?: {},
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(
                    if (isPromptAfterLogin) R.string.app_lock_prompt_body
                    else R.string.app_lock_setup_body
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            OutlinedTextField(
                value = pin,
                onValueChange = { new -> pin = new.filter { it.isDigit() }.take(6) },
                label = { Text(stringResource(R.string.app_lock_pin_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { new -> confirmPin = new.filter { it.isDigit() }.take(6) },
                label = { Text(stringResource(R.string.app_lock_pin_confirm_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                isError = confirmPin.isNotEmpty() && !match,
                supportingText = {
                    if (confirmPin.isNotEmpty() && !match)
                        Text(stringResource(R.string.app_lock_pin_mismatch))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.biometricAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = useBiometric, onCheckedChange = { useBiometric = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.app_lock_also_enable_biometric))
                }
            }
            Button(
                enabled = match,
                onClick = {
                    viewModel.setupPin(pin, useBiometric && state.biometricAvailable)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.app_lock_save)) }

            if (isPromptAfterLogin) {
                TextButton(
                    onClick = { viewModel.skipPrompt(); onDone() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.app_lock_skip_for_now)) }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    LaunchedEffect(Unit) { viewModel.refresh(activity) }
    val state by viewModel.state.collectAsState()

    var pin by remember { mutableStateOf("") }

    val title = stringResource(R.string.app_lock_biometric_title)
    val subtitle = stringResource(R.string.app_lock_biometric_subtitle)
    val cancelLabel = stringResource(R.string.app_lock_use_pin_instead)

    // Auto-trigger biometric prompt once on open if enabled.
    LaunchedEffect(state.mode) {
        val fragActivity = activity as? FragmentActivity
        if (fragActivity != null &&
            state.mode == com.timmat.financetracker.data.model.AppLockMode.PinAndBiometric &&
            state.biometricAvailable
        ) {
            AppLockViewModel.launchBiometricPrompt(
                activity = fragActivity,
                title = title,
                subtitle = subtitle,
                cancelLabel = cancelLabel,
                onSuccess = { viewModel.onBiometricSuccess() },
                onFail = { },
            )
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.app_lock_unlock_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.app_lock_unlock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            OutlinedTextField(
                value = pin,
                onValueChange = { new ->
                    val clean = new.filter { it.isDigit() }.take(6)
                    pin = clean
                    if (clean.length in 4..6) {
                        viewModel.verifyPin(clean)
                        pin = ""
                    }
                },
                label = { Text(stringResource(R.string.app_lock_pin_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (state.mode == com.timmat.financetracker.data.model.AppLockMode.PinAndBiometric && state.biometricAvailable) {
                OutlinedButton(
                    onClick = {
                        val fragActivity = activity as? FragmentActivity ?: return@OutlinedButton
                        AppLockViewModel.launchBiometricPrompt(
                            activity = fragActivity,
                            title = title,
                            subtitle = subtitle,
                            cancelLabel = cancelLabel,
                            onSuccess = { viewModel.onBiometricSuccess() },
                            onFail = { },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.app_lock_use_biometric)) }
            }
        }
    }
}
