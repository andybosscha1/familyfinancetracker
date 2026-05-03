package com.timmat.financetracker.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.timmat.financetracker.data.model.Recurrence
import com.timmat.financetracker.data.model.Transaction
import com.timmat.financetracker.data.model.TxType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar
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

    fun observeCurrentMonth(familyId: String): Flow<List<Transaction>> = callbackFlow {
        val (start, end) = currentMonthRange()
        val reg = col
            .whereEqualTo("familyId", familyId)
            .whereGreaterThanOrEqualTo("date", Timestamp(start))
            .whereLessThan("date", Timestamp(end))
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) trySend(emptyList())
                else trySend(snap.toObjects(Transaction::class.java))
            }
        awaitClose { reg.remove() }
    }

    /** Observes transactions from the first day of (N months ago) up to now. */
    fun observeLastMonths(familyId: String, months: Int): Flow<List<Transaction>> = callbackFlow {
        val start = nMonthsAgoStart(months)
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
                "createdAt" to Timestamp.now(),
            )
        ).await()
    }

    suspend fun delete(txId: String) {
        col.document(txId).delete().await()
    }

    /**
     * Deletes all `recurrence == "none"` transactions for [familyId] whose `date`
     * is strictly before the first day of the current month. Batched, max 400 per call.
     * Returns the number deleted.
     */
    suspend fun cleanupOneOffsBeforeCurrentMonth(familyId: String): Int {
        val (start, _) = currentMonthRange()
        val snap = col
            .whereEqualTo("familyId", familyId)
            .whereEqualTo("recurrence", Recurrence.none.name)
            .whereLessThan("date", Timestamp(start))
            .limit(400)
            .get().await()

        if (snap.isEmpty) return 0
        val batch = firestore.batch()
        snap.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
        return snap.size()
    }

    private fun currentMonthRange(): Pair<Date, Date> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.time
        cal.add(Calendar.MONTH, 1)
        val end = cal.time
        return start to end
    }

    private fun nMonthsAgoStart(months: Int): Date = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.MONTH, -(months - 1))
    }.time
}
