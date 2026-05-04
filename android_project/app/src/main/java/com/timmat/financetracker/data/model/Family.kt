package com.timmat.financetracker.data.model

import com.google.firebase.firestore.DocumentId

data class Family(
    @DocumentId val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    /**
     * Denormalised list of member UIDs. Kept in sync by repositories when members
     * join/leave. Firestore security rules use this list to verify membership in O(1).
     */
    val memberIds: List<String> = emptyList(),
    /** Day-of-month (1..28) on which a new billing cycle starts. Defaults to 1. */
    val monthStartDay: Int = 1,
    /**
     * When true, on the first app launch of a new billing cycle every expense in
     * that cycle is automatically flipped back to `paid = false` (useful for
     * recurring monthly bills so members only need to re-tick what they’ve paid).
     */
    val autoResetPaidOnRollover: Boolean = false,
)
