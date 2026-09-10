package com.example.calcalc.data.model

import com.google.firebase.firestore.DocumentId

enum class FastingMode {
    /** No fasting tracking. */
    OFF,

    /** Ad-hoc: start a fast now, run it for a chosen duration. */
    TIMER,

    /** The same eating window every day, e.g. fast 20:00 → 12:00. */
    DAILY,
}

/**
 * Stored at `users/{uid}/fasting/config`.
 *
 * Times are minutes from local midnight rather than timestamps: a daily window is a
 * wall-clock rule ("I stop eating at 8pm"), not an instant, so it must not shift with dates
 * or timezones.
 */
data class FastingConfig(
    val mode: FastingMode = FastingMode.OFF,
    val dailyStartMinute: Int = 20 * 60,
    val dailyEndMinute: Int = 12 * 60,
) {
    /** A window of zero length is not a fast; treated as unset. */
    val hasValidDailyWindow: Boolean get() = dailyStartMinute != dailyEndMinute
}

/**
 * Stored at `users/{uid}/fasting/active`. Absent when no timer fast is running.
 *
 * Kept as timestamps so the countdown survives the app being killed — the remaining time is
 * always derived, never counted down in memory.
 */
data class ActiveFast(
    val startedAt: Long = 0L,
    val plannedEndAt: Long = 0L,
) {
    val isSet: Boolean get() = startedAt > 0L && plannedEndAt > startedAt
}

/** Stored at `users/{uid}/fasts/{id}` — one finished timer fast. */
data class FastRecord(
    @DocumentId val id: String = "",
    val startedAt: Long = 0L,
    val endedAt: Long = 0L,
    val plannedEndAt: Long = 0L,
) {
    /** True when the user made it to the duration they set. */
    val reachedGoal: Boolean get() = endedAt >= plannedEndAt

    val actualMinutes: Long get() = (endedAt - startedAt) / 60_000L
}
