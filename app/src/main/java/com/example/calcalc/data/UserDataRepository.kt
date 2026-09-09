package com.example.calcalc.data

import com.example.calcalc.data.model.EntrySource
import com.example.calcalc.data.model.FoodItem
import com.example.calcalc.data.model.JournalEntry
import com.example.calcalc.data.model.UserProfile
import com.example.calcalc.data.model.WeightLog
import com.example.calcalc.data.model.totalCalories
import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * All reads and writes for one signed-in user.
 *
 * Firestore's persistent cache is enabled by default, so these flows serve local data
 * immediately and writes are queued while offline — there is no separate local database.
 */
class UserDataRepository(
    private val uid: String,
    private val db: FirebaseFirestore = Firebase.firestore,
) {
    private val userDoc: DocumentReference get() = db.collection("users").document(uid)
    private val entries: CollectionReference get() = userDoc.collection("entries")
    private val weights: CollectionReference get() = userDoc.collection("weights")

    // --- Profile ---

    fun profileFlow(): Flow<UserProfile?> =
        userDoc.snapshots().map { snapshot ->
            if (!snapshot.exists()) null
            else runCatching { snapshot.toObject(UserProfile::class.java) }.getOrNull()
        }

    suspend fun saveProfile(profile: UserProfile) {
        userDoc.set(profile, SetOptions.merge()).await()
    }

    // --- Journal entries ---

    /** Every entry for one calendar day. */
    fun entriesForDay(date: LocalDate): Flow<List<JournalEntry>> =
        entries.whereEqualTo("dateLocal", date.toString())
            .snapshots()
            .map { snap -> snap.toEntries().sortedBy { it.createdAt } }

    /**
     * Newest entries first, capped at [limit]. The journal groups these by day client-side,
     * which avoids one query per visible day.
     */
    fun recentEntries(limit: Long = 400): Flow<List<JournalEntry>> =
        entries.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .snapshots()
            .map { snap -> snap.toEntries() }

    suspend fun entry(id: String): JournalEntry? =
        entries.document(id).get().await()
            .let { if (it.exists()) it.toObject(JournalEntry::class.java) else null }

    /** Creates a new entry, or overwrites [id] when editing an existing one. Returns the id. */
    suspend fun saveEntry(
        id: String?,
        date: LocalDate,
        items: List<FoodItem>,
        source: EntrySource,
    ): String {
        val now = System.currentTimeMillis()
        val doc = if (id.isNullOrEmpty()) entries.document() else entries.document(id)
        val existingCreatedAt = if (id.isNullOrEmpty()) now else entry(id)?.createdAt ?: now
        val payload = JournalEntry(
            dateLocal = date.toString(),
            createdAt = existingCreatedAt,
            updatedAt = now,
            items = items,
            totalCalories = items.totalCalories(),
            source = source,
        )
        // `id` is @DocumentId, so it is read-only for writes; SetOptions is not needed
        // because a save always replaces the whole entry.
        doc.set(payload).await()
        return doc.id
    }

    suspend fun deleteEntry(id: String) {
        entries.document(id).delete().await()
    }

    // --- Weight ---

    fun weightsDescending(limit: Long = 400): Flow<List<WeightLog>> =
        weights.orderBy("recordedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toObject(WeightLog::class.java) } }

    /** One document per day, so re-weighing on the same day corrects rather than duplicates. */
    suspend fun logWeight(date: LocalDate, weightKg: Double) {
        weights.document(date.toString())
            .set(WeightLog(weightKg = weightKg, recordedAt = System.currentTimeMillis()))
            .await()
    }

    suspend fun deleteWeight(date: LocalDate) {
        weights.document(date.toString()).delete().await()
    }

    private fun com.google.firebase.firestore.QuerySnapshot.toEntries(): List<JournalEntry> =
        documents.mapNotNull { runCatching { it.toObject(JournalEntry::class.java) }.getOrNull() }
}
