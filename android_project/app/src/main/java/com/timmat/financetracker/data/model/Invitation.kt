package com.timmat.financetracker.data.model

import com.google.firebase.firestore.DocumentId

/**
 * Document ID is the 6-digit invitation `code`. Invitation state machine:
 *
 *   pending   → admin created the code, not yet entered by anyone.
 *   requested → user entered the code (we recorded `requesterUserId`, `requesterName`);
 *               admin must now approve or reject.
 *   accepted  → admin approved; user has been added to `familyMembers` / `memberIds`.
 *   cancelled → admin cancelled / rejected.
 */
data class Invitation(
    @DocumentId val id: String = "",
    /** 6-digit numeric string (also the doc ID). */
    val code: String = "",
    /** Informational for the admin; not used for authorisation. Lower-cased. */
    val email: String = "",
    val familyId: String = "",
    val status: String = "pending",
    /** Populated when a user submits the code (status → "requested"). */
    val requesterUserId: String = "",
    val requesterName: String = "",
    val requesterEmail: String = "",
)
