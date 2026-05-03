package com.timmat.financetracker.data.model

import com.google.firebase.firestore.DocumentId

/** Scoped per family (never global). */
data class Category(
    @DocumentId val id: String = "",
    val name: String = "",
    val familyId: String = "",
)
