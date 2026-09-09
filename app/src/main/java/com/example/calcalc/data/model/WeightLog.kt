package com.example.calcalc.data.model

import com.google.firebase.firestore.DocumentId

/**
 * Stored at `users/{uid}/weights/{yyyy-MM-dd}` — the document id is the day, so logging
 * twice on the same day overwrites instead of creating a duplicate point.
 */
data class WeightLog(
    @DocumentId val dateLocal: String = "",
    val weightKg: Double = 0.0,
    val recordedAt: Long = 0L,
)
