package com.timmat.financetracker.data.model

import com.google.firebase.firestore.DocumentId

/**
 * Document ID is the 6-digit invitation `code`. The admin shares the code with
 * the invitee via email (out of band); the invitee types it on the Join screen.
 */
data class Invitation(
    @DocumentId val id: String = "",
    /** 6-digit numeric string. Redundantly stored in the doc for validation in rules. */
    val code: String = "",
    /** Informational for the admin; not used for authorisation. Lower-cased. */
    val email: String = "",
    val familyId: String = "",
    val status: String = "pending", // pending | accepted | cancelled
)
