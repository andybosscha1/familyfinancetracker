package com.timmat.financetracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.timmat.financetracker.data.model.User
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private val users = firestore.collection("users")

    /** Batched profile fetch; silently skips ids that fail. Max 30 per whereIn query. */
    suspend fun getByIds(uids: List<String>): Map<String, User> {
        if (uids.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, User>()
        uids.distinct().chunked(30).forEach { chunk ->
            runCatching {
                users.whereIn("uid", chunk).get().await()
            }.getOrNull()?.documents?.forEach { doc ->
                doc.toObject(User::class.java)?.let { u ->
                    result[u.uid.ifBlank { doc.id }] = u
                }
            }
        }
        return result
    }
}
