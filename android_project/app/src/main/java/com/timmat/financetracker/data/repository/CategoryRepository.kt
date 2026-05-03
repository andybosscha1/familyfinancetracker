package com.timmat.financetracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.timmat.financetracker.data.model.Category
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private val categories = firestore.collection("categories")

    fun observe(familyId: String): Flow<List<Category>> = callbackFlow {
        val reg = categories
            .whereEqualTo("familyId", familyId)
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) trySend(emptyList())
                else trySend(snap.toObjects(Category::class.java))
            }
        awaitClose { reg.remove() }
    }

    /** Admin-only (enforced by rules). */
    suspend fun add(familyId: String, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name required" }
        categories.add(mapOf("name" to trimmed, "familyId" to familyId)).await()
    }

    suspend fun delete(categoryId: String) {
        categories.document(categoryId).delete().await()
    }
}
