package com.timmat.financetracker.data.model

/** Minimal user profile stored in `users/{uid}`. */
data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
) {
    // No-arg constructor required by Firestore auto-deserialization.
    constructor() : this("", "", "")
}
