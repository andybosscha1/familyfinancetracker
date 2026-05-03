package com.timmat.financetracker.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.timmat.financetracker.common.DefaultCategories
import com.timmat.financetracker.data.model.Family
import com.timmat.financetracker.data.model.FamilyMember
import com.timmat.financetracker.data.model.Role
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FamilyRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private val families = firestore.collection("families")
    private val members = firestore.collection("familyMembers")
    private val categories = firestore.collection("categories")

    /**
     * Creates a new family and registers the creator as admin.
     * Two sequential commits so rules see the admin member before category seeding.
     */
    suspend fun createFamily(name: String, creatorUid: String): String {
        val familyRef = families.document()
        val memberRef = members.document(FamilyMember.docId(creatorUid, familyRef.id))

        firestore.batch().apply {
            set(
                familyRef,
                mapOf(
                    "name" to name.trim(),
                    "createdBy" to creatorUid,
                    "memberIds" to listOf(creatorUid),
                )
            )
            set(
                memberRef,
                mapOf(
                    "userId" to creatorUid,
                    "familyId" to familyRef.id,
                    "role" to Role.admin.name,
                )
            )
        }.commit().await()

        firestore.batch().apply {
            DefaultCategories.NAMES.forEach { catName ->
                val ref = categories.document()
                set(ref, mapOf("name" to catName, "familyId" to familyRef.id))
            }
        }.commit().await()

        return familyRef.id
    }

    suspend fun getFamily(familyId: String): Family? =
        families.document(familyId).get().await().toObject(Family::class.java)

    fun observeFamiliesForUser(userId: String): Flow<List<Family>> = callbackFlow {
        val reg = members.whereEqualTo("userId", userId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    trySend(emptyList()); return@addSnapshotListener
                }
                val familyIds = snap.documents.mapNotNull { it.getString("familyId") }
                if (familyIds.isEmpty()) { trySend(emptyList()); return@addSnapshotListener }
                families.whereIn("__name__", familyIds.take(30)).get()
                    .addOnSuccessListener { result ->
                        trySend(result.toObjects(Family::class.java))
                    }
                    .addOnFailureListener { trySend(emptyList()) }
            }
        awaitClose { reg.remove() }
    }

    fun observeMembers(familyId: String): Flow<List<FamilyMember>> = callbackFlow {
        val reg = members.whereEqualTo("familyId", familyId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) trySend(emptyList())
                else trySend(snap.toObjects(FamilyMember::class.java))
            }
        awaitClose { reg.remove() }
    }

    suspend fun currentUserRole(familyId: String, userId: String): Role? {
        val doc = members.document(FamilyMember.docId(userId, familyId)).get().await()
        return doc.toObject(FamilyMember::class.java)?.roleEnum
    }

    /** Admin removes a member from the family. */
    suspend fun removeMember(familyId: String, memberUserId: String) {
        val memberRef = members.document(FamilyMember.docId(memberUserId, familyId))
        val familyRef = families.document(familyId)
        firestore.runBatch { batch ->
            batch.delete(memberRef)
            batch.update(familyRef, "memberIds", FieldValue.arrayRemove(memberUserId))
        }.await()
    }

    /**
     * Admin approves a pending join request: atomically adds [requesterUid] to
     * `memberIds` and creates their `familyMembers` doc with role=member.
     * Caller must be admin of [familyId]; enforced both client- and server-side.
     */
    suspend fun addMemberByAdmin(familyId: String, requesterUid: String) {
        val memberRef = members.document(FamilyMember.docId(requesterUid, familyId))
        val familyRef = families.document(familyId)
        firestore.runBatch { batch ->
            batch.set(
                memberRef,
                mapOf(
                    "userId" to requesterUid,
                    "familyId" to familyId,
                    "role" to Role.member.name,
                )
            )
            batch.update(familyRef, "memberIds", FieldValue.arrayUnion(requesterUid))
        }.await()
    }

    /**
     * Cascade-deletes a whole family. Only the original creator can do this
     * (Firestore rules enforce `createdBy == uid`).
     *
     * Order matters: child docs first, then the family doc, then the caller's
     * own membership last. The reason: most child rules use `isMemberOf(...)`
     * which reads the families doc, and the families-doc delete rule itself
     * uses `isAdminOf(...)` which reads the caller's familyMembers doc. So we
     * preserve both until the very end.
     *
     * Each step batches docs in chunks of 400 to stay under Firestore's 500
     * write limit per batch.
     */
    suspend fun deleteFamilyCascade(familyId: String, currentUid: String) {
        val transactions = firestore.collection("transactions")
        val budgets = firestore.collection("budgets")
        val invitations = firestore.collection("invitations")

        deleteAllInQuery(transactions.whereEqualTo("familyId", familyId))
        deleteAllInQuery(budgets.whereEqualTo("familyId", familyId))
        deleteAllInQuery(categories.whereEqualTo("familyId", familyId))
        deleteAllInQuery(invitations.whereEqualTo("familyId", familyId))

        // Delete every OTHER member's familyMembers doc; keep the caller's
        // membership alive so the families-delete rule still sees them as admin.
        val memberDocs = members.whereEqualTo("familyId", familyId).get().await().documents
        val (own, others) = memberDocs.partition { it.getString("userId") == currentUid }
        deleteDocsInBatches(others)

        // Delete the family doc itself (rule: admin && createdBy == uid).
        families.document(familyId).delete().await()

        // Finally delete the caller's own membership doc (rule: userId == uid).
        deleteDocsInBatches(own)
    }

    private suspend fun deleteAllInQuery(q: Query) {
        val snap = q.get().await()
        if (snap.isEmpty) return
        deleteDocsInBatches(snap.documents)
    }

    private suspend fun deleteDocsInBatches(docs: List<DocumentSnapshot>) {
        if (docs.isEmpty()) return
        docs.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }
}
