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
)
