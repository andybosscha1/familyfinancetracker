package com.timmat.financetracker.data.model

import com.google.firebase.firestore.DocumentId

data class Invitation(
    @DocumentId val id: String = "",
    /** Always stored lowercase. Compared against `auth.token.email.lower()` in rules. */
    val email: String = "",
    val familyId: String = "",
    val status: String = "pending", // pending | accepted | cancelled
)
