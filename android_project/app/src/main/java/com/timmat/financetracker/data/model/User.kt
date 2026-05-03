package com.timmat.financetracker.data.model

import com.google.firebase.firestore.DocumentId

/**
 * Minimal user profile stored in `users/{uid}`.
 *
 * `firstName` / `lastName` are split from Google's `displayName` on first sign-in.
 * UI should render `"$firstName $lastName"` (falling back to `email`) — never the uid.
 */
data class User(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    /** Full original display name from the auth provider (fallback if split fails). */
    val displayName: String = "",
) {
    val fullName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { displayName }.ifBlank { email }
}
