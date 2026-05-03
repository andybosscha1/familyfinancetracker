package com.timmat.financetracker.ui.family

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
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
import com.timmat.financetracker.data.model.FamilyMember
import com.timmat.financetracker.data.model.Invitation
import com.timmat.financetracker.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyManagementScreen(
    familyId: String,
    onBack: () -> Unit,
    onFamilyDeleted: () -> Unit,
    viewModel: FamilyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(familyId) { viewModel.load(familyId) }

    var email by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface info/error as snackbars
    LaunchedEffect(state.info, state.error) {
        state.info?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    // Navigate away once the family has been deleted.
    LaunchedEffect(state.deleted) {
        if (state.deleted) onFamilyDeleted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.family_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                    stringResource(R.string.family_share_code_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    code,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        stringResource(R.string.family_invite_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.family_invite_email)) },
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
                    ) { Text(stringResource(R.string.family_generate_code)) }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                }

                // -------- Pending join requests (admin approval required) --------
                item {
                    Text(
                        stringResource(R.string.family_requests_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (state.pendingRequests.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.family_no_requests),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                } else {
                    items(state.pendingRequests, key = { it.id }) { inv ->
                        JoinRequestCard(
                            inv = inv,
                            onApprove = { viewModel.approveRequest(inv.id) },
                            onReject = { viewModel.rejectRequest(inv.id) },
                        )
                    }
                }

                item {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.family_invitations_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (state.pendingInvitations.isEmpty()) {
                    item {
                        Text(
                            "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                } else {
                    items(state.pendingInvitations, key = { it.id }) { inv ->
                        IssuedCodeCard(inv = inv, onCancel = { viewModel.cancelInvite(inv.id) })
                    }
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.family_member_notice),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // -------- Members --------
            item {
                HorizontalDivider()
                Text(
                    stringResource(R.string.family_members_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(state.members, key = { it.id }) { member ->
                MemberCard(
                    member = member,
                    profile = state.profiles[member.userId],
                    canRemove = state.isAdmin && state.family?.createdBy != member.userId,
                    onRemove = { viewModel.removeMember(familyId, member.userId) },
                )
            }

            // -------- Categories --------
            item {
                HorizontalDivider()
                Text(
                    stringResource(R.string.family_categories_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (state.isAdmin) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newCategory,
                            onValueChange = { newCategory = it },
                            label = { Text(stringResource(R.string.family_new_category)) },
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
                        ) { Text(stringResource(R.string.action_add)) }
                    }
                }
            }
            items(state.categories, key = { it.id }) { cat ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(cat.name, style = MaterialTheme.typography.bodyLarge)
                        if (state.isAdmin) {
                            IconButton(onClick = { viewModel.deleteCategory(cat.id) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                )
                            }
                        }
                    }
                }
            }

            // -------- Danger zone (creator only) --------
            if (state.isCreator) {
                item {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.family_danger_zone),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.family_delete_warning_short),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { showDeleteDialog = true },
                                enabled = !state.deleting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.deleting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.family_delete_button))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (showDeleteDialog) {
            DeleteFamilyDialog(
                familyName = state.family?.name.orEmpty(),
                onDismiss = { showDeleteDialog = false },
                onConfirm = {
                    showDeleteDialog = false
                    viewModel.deleteFamily(familyId)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteFamilyDialog(
    familyName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim() == familyName.trim() && familyName.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.family_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.family_delete_warning_long))
                Text(
                    stringResource(R.string.family_delete_type_name_hint, familyName),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.onboarding_family_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = matches,
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text(stringResource(R.string.family_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun JoinRequestCard(
    inv: Invitation,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                inv.requesterName.ifBlank { inv.requesterEmail.ifBlank { inv.email } },
                style = MaterialTheme.typography.titleMedium,
            )
            if (inv.requesterEmail.isNotBlank()) {
                Text(
                    inv.requesterEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Text(
                stringResource(R.string.family_request_code_label, inv.code),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_approve))
                }
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_reject))
                }
            }
        }
    }
}

@Composable
private fun IssuedCodeCard(inv: Invitation, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (inv.email.isNotBlank()) {
                    Text(inv.email, style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    inv.code,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
private fun MemberCard(
    member: FamilyMember,
    profile: User?,
    canRemove: Boolean,
    onRemove: () -> Unit,
) {
    val displayName = profile?.fullName
        ?.takeIf { it.isNotBlank() }
        ?: profile?.email?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.family_member_loading)
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    member.role,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            if (canRemove) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
            }
        }
    }
}
