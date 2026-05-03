package com.timmat.financetracker.data.model

import com.google.firebase.firestore.DocumentId

data class Budget(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val categoryId: String = "",
    val monthlyLimit: Double = 0.0,
)
