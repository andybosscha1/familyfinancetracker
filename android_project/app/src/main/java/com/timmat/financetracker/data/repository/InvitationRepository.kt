package com.timmat.financetracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.timmat.financetracker.data.model.Invitation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvitationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val familyRepository: FamilyRepository,
) {
    private val invitations = firestore.collection("invitations")

    /**
     * Admin creates an invite for `email` to join `familyId`.
     * Returns the 6-digit code to share with the invitee.
     *
     * Retries on code collision (chance is 1 in 1,000,000 per attempt).
     */
    suspend fun invite(familyId: String, email: String): String {
        val normalized = email.trim().lowercase()
        require(normalized.isNotEmpty() && normalized.contains("@")) { "Invalid email" }

        repeat(8) {
            val code = generateCode()
            val ref = invitations.document(code)
            val existing = ref.get().await()
            if (!existing.exists()) {
                ref.set(
                    mapOf(
                        "code" to code,
                        "email" to normalized,
                        "familyId" to familyId,
                        "status" to "pending",
                    )
                ).await()
                return code
            }
        }
        throw IllegalStateException("Unable to generate a unique invitation code, please try again")
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
     * User accepts an invitation using a 6-digit [code]. Performs three steps:
     *   1. Fetches the invitation doc (doc ID == code); validates status/format.
     *   2. Adds self to the target family (familyMembers + memberIds[] update).
     *   3. Flips the invitation status to "accepted".
     *
     * Returns the joined familyId on success.
     * Throws with a human-readable message otherwise.
     */
    suspend fun acceptByCode(userId: String, code: String): String {
        val trimmed = code.trim()
        require(trimmed.length == 6 && trimmed.all { it.isDigit() }) {
            "Code must be 6 digits"
        }

        val ref = invitations.document(trimmed)
        val snap = ref.get().await()
        if (!snap.exists()) throw IllegalStateException("Invalid code")

        val status = snap.getString("status")
        val familyId = snap.getString("familyId")
            ?: throw IllegalStateException("Invitation missing familyId")

        when (status) {
            "accepted" -> throw IllegalStateException("This code has already been used")
            "cancelled" -> throw IllegalStateException("This invitation was cancelled")
            "pending" -> Unit
            else -> throw IllegalStateException("Invitation is not usable")
        }

        familyRepository.addSelfAsMember(familyId, userId)
        ref.update("status", "accepted").await()
        return familyId
    }

    suspend fun cancel(code: String) {
        invitations.document(code).update("status", "cancelled").await()
    }

    private fun generateCode(): String =
        Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
}
