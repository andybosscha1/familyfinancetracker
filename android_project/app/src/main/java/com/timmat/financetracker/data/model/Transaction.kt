package com.timmat.financetracker.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

enum class TxType { income, expense }
enum class Recurrence { none, monthly }

data class Transaction(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val userId: String = "",
    val amount: Double = 0.0,
    val type: String = TxType.expense.name,
    val categoryId: String = "",
    val date: Timestamp = Timestamp.now(),
    val recurrence: String = Recurrence.none.name,
    /** Only meaningful for expenses. true = bill has been paid. */
    val paid: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
) {
    val typeEnum: TxType
        get() = runCatching { TxType.valueOf(type) }.getOrDefault(TxType.expense)

    val recurrenceEnum: Recurrence
        get() = runCatching { Recurrence.valueOf(recurrence) }.getOrDefault(Recurrence.none)
}
