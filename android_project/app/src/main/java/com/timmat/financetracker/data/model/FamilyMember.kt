package com.timmat.financetracker.data.model

import com.google.firebase.firestore.DocumentId

/**
 * Document ID convention: `"{userId}_{familyId}"` — deterministic so the
 * security rules can look it up in a single `get()` without a query.
 */
data class FamilyMember(
    @DocumentId val id: String = "",
    val userId: String = "",
    val familyId: String = "",
    val role: String = Role.member.name,
) {
    val roleEnum: Role
        get() = runCatching { Role.valueOf(role) }.getOrDefault(Role.member)

    companion object {
        fun docId(userId: String, familyId: String): String = "${userId}_$familyId"
    }
}
