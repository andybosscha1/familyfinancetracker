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
                    "monthStartDay" to 1,
                    "autoResetPaidOnRollover" to false,
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

    fun observeFamily(familyId: String): Flow<Family?> = callbackFlow {
        val reg = families.document(familyId).addSnapshotListener { snap, err ->
            if (err != null || snap == null) trySend(null)
            else trySend(snap.toObject(Family::class.java))
        }
        awaitClose { reg.remove() }
    }

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

    suspend fun removeMember(familyId: String, memberUserId: String) {
        val memberRef = members.document(FamilyMember.docId(memberUserId, familyId))
        val familyRef = families.document(familyId)
        firestore.runBatch { batch ->
            batch.delete(memberRef)
            batch.update(familyRef, "memberIds", FieldValue.arrayRemove(memberUserId))
        }.await()
    }

    /** Admin promotes or demotes another member. */
    suspend fun setMemberRole(familyId: String, memberUserId: String, role: Role) {
        members.document(FamilyMember.docId(memberUserId, familyId))
            .update(
                mapOf(
                    "userId" to memberUserId,
                    "familyId" to familyId,
                    "role" to role.name,
                )
            ).await()
    }

    /** Admin-only: update billing-cycle preferences (monthStartDay + autoResetPaidOnRollover). */
    suspend fun updateCycleSettings(familyId: String, monthStartDay: Int, autoReset: Boolean) {
        val day = monthStartDay.coerceIn(1, 28)
        families.document(familyId).update(
            mapOf(
                "monthStartDay" to day,
                "autoResetPaidOnRollover" to autoReset,
            )
        ).await()
    }

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

    suspend fun deleteFamilyCascade(familyId: String, currentUid: String) {
        val transactions = firestore.collection("transactions")
        val budgets = firestore.collection("budgets")
        val invitations = firestore.collection("invitations")

        deleteAllInQuery(transactions.whereEqualTo("familyId", familyId))
        deleteAllInQuery(budgets.whereEqualTo("familyId", familyId))
        deleteAllInQuery(categories.whereEqualTo("familyId", familyId))
        deleteAllInQuery(invitations.whereEqualTo("familyId", familyId))

        val memberDocs = members.whereEqualTo("familyId", familyId).get().await().documents
        val (own, others) = memberDocs.partition { it.getString("userId") == currentUid }
        deleteDocsInBatches(others)

        families.document(familyId).delete().await()
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
