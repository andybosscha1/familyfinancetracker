package com.timmat.financetracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.timmat.financetracker.data.model.Invitation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvitationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val familyRepository: FamilyRepository,
) {
    private val invitations = firestore.collection("invitations")

    /** Admin creates an invite for `email` to join `familyId`. Email is lowercased. */
    suspend fun invite(familyId: String, email: String) {
        val normalized = email.trim().lowercase()
        require(normalized.isNotEmpty() && normalized.contains("@")) { "Invalid email" }
        invitations.add(
            mapOf(
                "email" to normalized,
                "familyId" to familyId,
                "status" to "pending",
            )
        ).await()
    }

    fun observeInvitationsForFamily(familyId: String): Flow<List<Invitation>> = callbackFlow {
        val reg = invitations
            .whereEqualTo("familyId", familyId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) trySend(emptyList())
                else trySend(snap.toObjects(Invitation::class.java))
            }
        awaitClose { reg.remove() }
    }

    /**
     * On login: find all pending invitations for the user's email,
     * join each family, and mark invitations accepted. Returns the number accepted.
     */
    suspend fun processPendingInvitationsForUser(userId: String, email: String): Int {
        val normalized = email.lowercase()
        val pending = invitations
            .whereEqualTo("email", normalized)
            .whereEqualTo("status", "pending")
            .get().await()

        var accepted = 0
        for (doc in pending.documents) {
            val familyId = doc.getString("familyId") ?: continue

            // Join the family first; if that fails the invite stays pending (safe to retry).
            runCatching {
                familyRepository.addSelfAsMember(familyId, userId)
                doc.reference.update("status", "accepted").await()
                accepted++
            }
        }
        return accepted
    }

    suspend fun cancel(invitationId: String) {
        invitations.document(invitationId).update("status", "cancelled").await()
    }
}
