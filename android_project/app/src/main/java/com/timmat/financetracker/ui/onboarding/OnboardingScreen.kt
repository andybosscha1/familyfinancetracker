package com.timmat.financetracker.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFamilyReady: (String) -> Unit,
    onSignOut: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var familyName by remember { mutableStateOf("") }

    // Auto-navigate when the user has exactly one family (common case: invited user, or just created).
    LaunchedEffect(state.families) {
        if (!state.checking && state.families.size == 1) {
            onFamilyReady(state.families.first().id)
        }
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.checking -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                state.families.isNotEmpty() -> {
                    Text("Your families", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                }
            }

            Text("Create a new family", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = familyName,
                onValueChange = { familyName = it },
                label = { Text("Family name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.createFamily(familyName) { onFamilyReady(it) } },
                enabled = !state.creating && familyName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.creating) "Creating…" else "Create family")
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            OutlinedButton(
                onClick = { viewModel.refresh() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Refresh invitations") }
        }
    }
}
