package com.timmat.financetracker.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.timmat.financetracker.common.currentBillingCycle
import com.timmat.financetracker.common.cycleStartMonthsAgo
import com.timmat.financetracker.data.model.Recurrence
import com.timmat.financetracker.data.model.Transaction
import com.timmat.financetracker.data.model.TxType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private val col = firestore.collection("transactions")

    fun observeForFamily(familyId: String, limit: Long = 200): Flow<List<Transaction>> = callbackFlow {
        val reg = col
            .whereEqualTo("familyId", familyId)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) trySend(emptyList())
                else trySend(snap.toObjects(Transaction::class.java))
            }
        awaitClose { reg.remove() }
    }

    fun observeCurrentMonth(familyId: String, monthStartDay: Int = 1): Flow<List<Transaction>> = callbackFlow {
        val cycle = currentBillingCycle(monthStartDay)
        val reg = col
            .whereEqualTo("familyId", familyId)
            .whereGreaterThanOrEqualTo("date", Timestamp(cycle.start))
            .whereLessThan("date", Timestamp(cycle.end))
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) trySend(emptyList())
                else trySend(snap.toObjects(Transaction::class.java))
            }
        awaitClose { reg.remove() }
    }

    fun observeLastMonths(familyId: String, months: Int, monthStartDay: Int = 1): Flow<List<Transaction>> = callbackFlow {
        val start = cycleStartMonthsAgo(monthStartDay, months)
        val reg = col
            .whereEqualTo("familyId", familyId)
            .whereGreaterThanOrEqualTo("date", Timestamp(start))
            .orderBy("date", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) trySend(emptyList())
                else trySend(snap.toObjects(Transaction::class.java))
            }
        awaitClose { reg.remove() }
    }

    suspend fun getById(txId: String): Transaction? =
        col.document(txId).get().await().toObject(Transaction::class.java)

    suspend fun add(
        familyId: String,
        userId: String,
        amount: Double,
        type: TxType,
        categoryId: String,
        date: Date,
        recurrence: Recurrence,
    ) {
        require(amount > 0) { "Amount must be positive" }
        require(categoryId.isNotBlank()) { "Category required" }
        col.add(
            mapOf(
                "familyId" to familyId,
                "userId" to userId,
                "amount" to amount,
                "type" to type.name,
                "categoryId" to categoryId,
                "date" to Timestamp(date),
                "recurrence" to recurrence.name,
                "paid" to false,
                "createdAt" to Timestamp.now(),
            )
        ).await()
    }

    suspend fun update(
        txId: String,
        amount: Double,
        type: TxType,
        categoryId: String,
        date: Date,
        recurrence: Recurrence,
    ) {
        require(amount > 0) { "Amount must be positive" }
        require(categoryId.isNotBlank()) { "Category required" }
        col.document(txId).update(
            mapOf(
                "amount" to amount,
                "type" to type.name,
                "categoryId" to categoryId,
                "date" to Timestamp(date),
                "recurrence" to recurrence.name,
            )
        ).await()
    }

    suspend fun delete(txId: String) {
        col.document(txId).delete().await()
    }

    suspend fun setPaid(txId: String, paid: Boolean) {
        col.document(txId).update("paid", paid).await()
    }

    suspend fun markAllExpensesUnpaidForCurrentMonth(familyId: String, monthStartDay: Int = 1): Int {
        val cycle = currentBillingCycle(monthStartDay)
        val snap = col
            .whereEqualTo("familyId", familyId)
            .whereEqualTo("type", TxType.expense.name)
            .whereGreaterThanOrEqualTo("date", Timestamp(cycle.start))
            .whereLessThan("date", Timestamp(cycle.end))
            .get().await()
        if (snap.isEmpty) return 0
        snap.documents.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc -> batch.update(doc.reference, "paid", false) }
            batch.commit().await()
        }
        return snap.size()
    }

    suspend fun cleanupOneOffsBeforeCurrentMonth(familyId: String, monthStartDay: Int = 1): Int {
        val cycle = currentBillingCycle(monthStartDay)
        val snap = col
            .whereEqualTo("familyId", familyId)
            .whereEqualTo("recurrence", Recurrence.none.name)
            .whereLessThan("date", Timestamp(cycle.start))
            .limit(400)
            .get().await()
        if (snap.isEmpty) return 0
        val batch = firestore.batch()
        snap.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
        return snap.size()
    }
}
