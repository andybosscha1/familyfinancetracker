package com.timmat.financetracker.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.timmat.financetracker.R

private enum class OnboardingMode { Create, Join }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFamilyReady: (String) -> Unit,
    onSignOut: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var mode by remember { mutableStateOf(OnboardingMode.Create) }
    var familyName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.onboarding_title)) },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.nav_settings))
                    }
                    TextButton(onClick = {
                        viewModel.signOut(); onSignOut()
                    }) { Text(stringResource(R.string.action_sign_out)) }
                },
            )
        }
    ) { padding ->
        if (state.checking) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.families.isNotEmpty()) {
                Text(stringResource(R.string.onboarding_your_families),
                    style = MaterialTheme.typography.titleMedium)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.families, key = { it.id }) { family ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(family.name, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.onboarding_member_count, family.memberIds.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { onFamilyReady(family.id) }) {
                                    Text(stringResource(R.string.action_open))
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.onboarding_add_another),
                    style = MaterialTheme.typography.titleMedium)
            } else {
                Text(stringResource(R.string.onboarding_intro_title),
                    style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.onboarding_intro_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == OnboardingMode.Create,
                    onClick = { mode = OnboardingMode.Create; viewModel.clearMessages() },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.onboarding_mode_create)) }
                SegmentedButton(
                    selected = mode == OnboardingMode.Join,
                    onClick = { mode = OnboardingMode.Join; viewModel.clearMessages() },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.onboarding_mode_join)) }
            }

            if (mode == OnboardingMode.Create) {
                Text(stringResource(R.string.onboarding_create_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                OutlinedTextField(
                    value = familyName,
                    onValueChange = { familyName = it },
                    label = { Text(stringResource(R.string.onboarding_family_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = !state.busy && familyName.isNotBlank(),
                    onClick = { viewModel.createFamily(familyName) { onFamilyReady(it) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.busy) stringResource(R.string.onboarding_creating)
                         else stringResource(R.string.onboarding_create_button))
                }
            } else {
                Text(stringResource(R.string.onboarding_join_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { new -> joinCode = new.filter { it.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.onboarding_invitation_code)) },
                    placeholder = { Text("123456") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = !state.busy && joinCode.length == 6 && !state.requestSubmitted,
                    onClick = { viewModel.submitJoinRequest(joinCode) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.busy) stringResource(R.string.onboarding_joining)
                         else stringResource(R.string.onboarding_join_button))
                }
                if (state.requestSubmitted) {
                    Text(
                        stringResource(R.string.onboarding_request_submitted),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.info?.takeIf { !state.requestSubmitted }?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
