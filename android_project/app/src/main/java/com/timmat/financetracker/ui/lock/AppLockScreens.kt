package com.timmat.financetracker.ui.lock

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.timmat.financetracker.R
import com.timmat.financetracker.common.UiMessage
import com.timmat.financetracker.common.resolve
import com.timmat.financetracker.data.model.AppLockMode

// ============================================================================
//  SETUP SCREEN (choose PIN + optional biometric)
// ============================================================================

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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                Icons.Filled.Lock, null,
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

            state.error?.resolve()?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

// ============================================================================
//  UNLOCK SCREEN (custom 0-9 keypad)
// ============================================================================

@Composable
fun AppLockScreen(viewModel: AppLockViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    LaunchedEffect(Unit) { viewModel.refresh(activity) }
    val state by viewModel.state.collectAsState()

    var entered by remember { mutableStateOf("") }

    // Auto-verify when entered PIN reaches the expected length.
    LaunchedEffect(entered) {
        if (entered.length == state.pinLength && state.pinLength >= 4) {
            val ok = viewModel.verifyPin(entered)
            if (!ok) entered = ""
        }
    }

    val bioTitle = stringResource(R.string.app_lock_biometric_title)
    val bioSubtitle = stringResource(R.string.app_lock_biometric_subtitle)
    val bioCancel = stringResource(R.string.app_lock_use_pin_instead)

    // Auto-launch biometric on open (once).
    var biometricAutoLaunched by remember { mutableStateOf(false) }
    LaunchedEffect(state.mode, state.biometricAvailable) {
        if (!biometricAutoLaunched &&
            state.mode == AppLockMode.PinAndBiometric &&
            state.biometricAvailable
        ) {
            biometricAutoLaunched = true
            val fragAct = activity as? FragmentActivity ?: return@LaunchedEffect
            AppLockViewModel.launchBiometricPrompt(
                activity = fragAct,
                title = bioTitle, subtitle = bioSubtitle, cancelLabel = bioCancel,
                onSuccess = { viewModel.onBiometricSuccess() },
                onFail = { },
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Icon(
                Icons.Filled.Lock, null,
                modifier = Modifier.size(56.dp),
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

            PinDots(length = state.pinLength, entered = entered.length)

            val errorText = state.error?.resolve()
            if (!errorText.isNullOrBlank()) {
                Text(errorText, color = MaterialTheme.colorScheme.error)
            } else {
                Spacer(Modifier.height(20.dp))
            }

            Spacer(Modifier.height(8.dp))

            PinKeypad(
                enabled = state.lockoutSecondsLeft == 0,
                showBiometric = state.mode == AppLockMode.PinAndBiometric && state.biometricAvailable,
                onDigit = { d ->
                    if (entered.length < state.pinLength) entered += d
                },
                onBackspace = { if (entered.isNotEmpty()) entered = entered.dropLast(1) },
                onBiometric = {
                    val fragAct = activity as? FragmentActivity ?: return@PinKeypad
                    AppLockViewModel.launchBiometricPrompt(
                        activity = fragAct,
                        title = bioTitle, subtitle = bioSubtitle, cancelLabel = bioCancel,
                        onSuccess = { viewModel.onBiometricSuccess() },
                        onFail = { },
                    )
                },
            )
        }
    }
}

@Composable
private fun PinDots(length: Int, entered: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val len = length.coerceAtLeast(4)
        repeat(len) { i ->
            val filled = i < entered
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}

@Composable
private fun PinKeypad(
    enabled: Boolean,
    showBiometric: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { d -> KeypadDigit(d, enabled) { onDigit(d) } }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBiometric) {
                KeypadAction(enabled = enabled, onClick = onBiometric) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = stringResource(R.string.app_lock_use_biometric))
                }
            } else {
                Spacer(Modifier.size(KEY_SIZE))
            }
            KeypadDigit("0", enabled) { onDigit("0") }
            KeypadAction(enabled = enabled, onClick = onBackspace) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
            }
        }
    }
}

private val KEY_SIZE = 72.dp

@Composable
private fun KeypadDigit(digit: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.size(KEY_SIZE),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(digit, fontSize = 26.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun KeypadAction(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.size(KEY_SIZE),
        contentPadding = PaddingValues(0.dp),
    ) { content() }
}
