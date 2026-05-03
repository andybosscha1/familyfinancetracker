package com.timmat.financetracker.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyManagementScreen(
    familyId: String,
    onBack: () -> Unit,
    viewModel: FamilyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(familyId) { viewModel.load(familyId) }

    var email by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isAdmin) {
                state.lastCreatedCode?.let { code ->
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "Share this 6-digit code with the invitee:",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    code,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Tell them to enter it on the \"Join with code\" screen after signing in.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }

                item {
                    Text("Invite a member", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = email.contains("@"),
                        onClick = {
                            viewModel.invite(familyId, email)
                            email = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Generate invitation code") }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                }
            } else {
                item {
                    Text(
                        "You are a member. Only admins can invite or remove users and manage categories.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item { Text("Members", style = MaterialTheme.typography.titleMedium) }
            items(state.members, key = { it.id }) { member ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(member.userId, style = MaterialTheme.typography.bodyLarge)
                            Text(member.role, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (state.isAdmin) {
                            TextButton(onClick = { viewModel.removeMember(familyId, member.userId) }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                Text("Pending invitations", style = MaterialTheme.typography.titleMedium)
            }
            items(state.invitations, key = { it.id }) { inv ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(inv.email, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Code: ${inv.code}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(inv.status, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (state.isAdmin && inv.status == "pending") {
                            TextButton(onClick = { viewModel.cancelInvite(inv.id) }) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                Text("Categories", style = MaterialTheme.typography.titleMedium)
            }
            if (state.isAdmin) {
                item {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newCategory,
                            onValueChange = { newCategory = it },
                            label = { Text("New category") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = newCategory.isNotBlank(),
                            onClick = {
                                viewModel.addCategory(familyId, newCategory)
                                newCategory = ""
                            },
                        ) { Text("Add") }
                    }
                }
            }
            items(state.categories, key = { it.id }) { cat ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(cat.name, style = MaterialTheme.typography.bodyLarge)
                        if (state.isAdmin) {
                            IconButton(onClick = { viewModel.deleteCategory(cat.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }

            state.error?.let { err ->
                item { Text(err, color = MaterialTheme.colorScheme.error) }
            }
            state.info?.let { msg ->
                item { Text(msg, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}
