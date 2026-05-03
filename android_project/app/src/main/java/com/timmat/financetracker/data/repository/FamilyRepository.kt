package com.timmat.financetracker.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
}
