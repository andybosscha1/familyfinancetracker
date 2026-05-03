package com.timmat.financetracker.ui.onboarding

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private enum class OnboardingMode { Create, Join }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFamilyReady: (String) -> Unit,
    onSignOut: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var mode by remember { mutableStateOf(OnboardingMode.Create) }
    var familyName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Welcome") },
                actions = {
                    TextButton(onClick = {
                        viewModel.signOut()
                        onSignOut()
                    }) { Text("Sign out") }
                },
            )
        }
    ) { padding ->
        if (state.checking) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // If the user already belongs to one or more families, list them so they can open one.
            if (state.families.isNotEmpty()) {
                Text("Your families", style = MaterialTheme.typography.titleMedium)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(state.families, key = { it.id }) { family ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(family.name, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${family.memberIds.size} member(s)",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { onFamilyReady(family.id) }) {
                                    Text("Open")
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    "Add another family",
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Text(
                    "Let's get you set up.",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "Create a new family as its admin, or join one that invited you.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }

            // Mode selector
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == OnboardingMode.Create,
                    onClick = { mode = OnboardingMode.Create; viewModel.clearMessages() },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Create family") }
                SegmentedButton(
                    selected = mode == OnboardingMode.Join,
                    onClick = { mode = OnboardingMode.Join; viewModel.clearMessages() },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Join with code") }
            }

            when (mode) {
                OnboardingMode.Create -> CreateFamilyForm(
                    name = familyName,
                    onNameChange = { familyName = it },
                    busy = state.busy,
                    onSubmit = {
                        viewModel.createFamily(familyName) { onFamilyReady(it) }
                    },
                )
                OnboardingMode.Join -> JoinFamilyForm(
                    code = joinCode,
                    onCodeChange = { new -> joinCode = new.filter { it.isDigit() }.take(6) },
                    busy = state.busy,
                    onSubmit = {
                        viewModel.joinByCode(joinCode) { onFamilyReady(it) }
                    },
                )
            }

            state.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error)
            }
            state.info?.let { info ->
                Text(info, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CreateFamilyForm(
    name: String,
    onNameChange: (String) -> Unit,
    busy: Boolean,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            "You'll become the admin of the new family.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Family name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onSubmit,
            enabled = !busy && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Creating…" else "Create family") }
    }
}

@Composable
private fun JoinFamilyForm(
    code: String,
    onCodeChange: (String) -> Unit,
    busy: Boolean,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            "Enter the 6-digit code from your invitation email.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text("Invitation code") },
            placeholder = { Text("123456") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onSubmit,
            enabled = !busy && code.length == 6,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Joining…" else "Join family") }
    }
}
