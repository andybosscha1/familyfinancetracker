package com.timmat.financetracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.timmat.financetracker.data.model.Budget
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private val col = firestore.collection("budgets")

    fun observe(familyId: String): Flow<List<Budget>> = callbackFlow {
        val reg = col
            .whereEqualTo("familyId", familyId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) trySend(emptyList())
                else trySend(snap.toObjects(Budget::class.java))
            }
        awaitClose { reg.remove() }
    }

    /** Admin-only. Upserts one budget per (familyId, categoryId). */
    suspend fun upsert(familyId: String, categoryId: String, monthlyLimit: Double) {
        require(monthlyLimit >= 0) { "Limit must be non-negative" }
        val existing = col
            .whereEqualTo("familyId", familyId)
            .whereEqualTo("categoryId", categoryId)
            .limit(1).get().await()

        val payload = mapOf(
            "familyId" to familyId,
            "categoryId" to categoryId,
            "monthlyLimit" to monthlyLimit,
        )
        if (existing.isEmpty) {
            col.add(payload).await()
        } else {
            existing.documents.first().reference.set(payload).await()
        }
    }

    suspend fun delete(budgetId: String) {
        col.document(budgetId).delete().await()
    }
}
