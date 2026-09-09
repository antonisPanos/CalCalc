package com.example.calcalc.data.model

import com.google.firebase.firestore.DocumentId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One food line as parsed by Gemini. Macros are optional — not every estimate has them. */
data class FoodItem(
    val name: String = "",
    val quantity: String = "",
    val calories: Int = 0,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    /** Model's own confidence, 0..1. Shown as a hint when it is low. */
    val confidence: Double? = null,
)

enum class EntrySource { TEXT, PHOTO }

/**
 * Stored at `users/{uid}/entries/{id}`. One entry is one "Done" press in the chat,
 * i.e. usually one meal.
 */
data class JournalEntry(
    @DocumentId val id: String = "",
    /** Local calendar day, `yyyy-MM-dd`. Kept as a string so day grouping needs no timezone math. */
    val dateLocal: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val items: List<FoodItem> = emptyList(),
    val totalCalories: Int = 0,
    val source: EntrySource = EntrySource.TEXT,
) {
    val date: LocalDate? get() = dateLocal.toLocalDateOrNull()
}

val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun LocalDate.toDateLocal(): String = format(ISO_DATE)

fun List<FoodItem>.totalCalories(): Int = sumOf { it.calories }
