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

/**
 * Admin-approval invitation flow:
 *
 *   admin.invite(familyId, email)         → status = "pending",  doc id = 6-digit code
 *   user.submitRequest(code, uid, name)   → status = "requested", requesterUserId = uid
 *   admin.approve(code)                   → familyRepo.addMemberByAdmin(...) then status = "accepted"
 *   admin.reject(code)                    → status = "cancelled"
 */
@Singleton
class InvitationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val familyRepository: FamilyRepository,
) {
    private val invitations = firestore.collection("invitations")

    suspend fun invite(familyId: String, email: String): String {
        val normalized = email.trim().lowercase()
        require(normalized.isNotEmpty() && normalized.contains("@")) { "Invalid email" }

        repeat(8) {
            val code = generateCode()
            val ref = invitations.document(code)
            if (!ref.get().await().exists()) {
                ref.set(
                    mapOf(
                        "code" to code,
                        "email" to normalized,
                        "familyId" to familyId,
                        "status" to "pending",
                        "requesterUserId" to "",
                        "requesterName" to "",
                        "requesterEmail" to "",
                    )
                ).await()
                return code
            }
        }
        throw IllegalStateException("Could not generate a unique code, please try again")
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

    /** User submits a join request. Returns the familyId of the requested family. */
    suspend fun submitRequest(
        code: String,
        userId: String,
        userName: String,
        userEmail: String,
    ): String {
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
            "accepted"  -> throw IllegalStateException("This code has already been used")
            "cancelled" -> throw IllegalStateException("This invitation was cancelled")
            "requested" -> throw IllegalStateException("This code is awaiting admin approval")
            "pending"   -> Unit
            else        -> throw IllegalStateException("Invitation is not usable")
        }

        ref.update(
            mapOf(
                "status" to "requested",
                "requesterUserId" to userId,
                "requesterName" to userName,
                "requesterEmail" to userEmail.lowercase(),
            )
        ).await()
        return familyId
    }

    /** Admin approves a request: add the member + flip status to accepted. */
    suspend fun approve(code: String) {
        val ref = invitations.document(code)
        val snap = ref.get().await()
        val status = snap.getString("status")
        val familyId = snap.getString("familyId") ?: error("Missing familyId")
        val requesterUid = snap.getString("requesterUserId").orEmpty()
        require(status == "requested") { "Invitation is not in 'requested' state" }
        require(requesterUid.isNotBlank()) { "No requester to approve" }

        familyRepository.addMemberByAdmin(familyId, requesterUid)
        ref.update("status", "accepted").await()
    }

    suspend fun reject(code: String) {
        invitations.document(code).update("status", "cancelled").await()
    }

    suspend fun delete(code: String) {
        invitations.document(code).delete().await()
    }

    private fun generateCode(): String =
        Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
}
