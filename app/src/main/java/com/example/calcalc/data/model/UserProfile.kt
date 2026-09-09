package com.example.calcalc.data.model

import java.time.LocalDate

enum class Sex { MALE, FEMALE }

/**
 * Activity multipliers applied to BMR to get TDEE. Labels are phrased as jobs/lifestyles
 * rather than as abstract "levels" so the onboarding question is answerable without guessing.
 */
enum class ActivityLevel(val multiplier: Double, val label: String, val detail: String) {
    SEDENTARY(1.2, "Desk job", "Mostly sitting, little or no exercise"),
    LIGHT(1.375, "Lightly active", "Light exercise 1-3 days a week"),
    MODERATE(1.55, "Moderately active", "Moderate exercise 3-5 days a week"),
    ACTIVE(1.725, "On your feet all day", "Construction, warehouse, or hard exercise 6-7 days"),
    ATHLETE(1.9, "Athlete", "Physical job plus training, or two sessions a day"),
}

enum class Goal { MAINTAIN, LOSE, GAIN }

/**
 * Stored at `users/{uid}`. Every field has a default because Firestore's `toObject`
 * needs a no-arg constructor, and older documents may be missing newer fields.
 */
data class UserProfile(
    val displayName: String = "",
    val sex: Sex = Sex.MALE,
    /** ISO-8601 `yyyy-MM-dd`; Firestore has no date-only type. */
    val birthDate: String = "",
    val heightCm: Double = 0.0,
    /** Starting weight. The live value comes from the newest weight log, if any. */
    val weightKg: Double = 0.0,
    val activityLevel: ActivityLevel = ActivityLevel.SEDENTARY,
    val goal: Goal = Goal.MAINTAIN,
    val goalWeightKg: Double? = null,
    /** ISO-8601 `yyyy-MM-dd`, or null for "no deadline". */
    val goalDate: String? = null,
) {
    val birthDateOrNull: LocalDate? get() = birthDate.toLocalDateOrNull()
    val goalDateOrNull: LocalDate? get() = goalDate?.toLocalDateOrNull()

    /** True once onboarding has collected everything the calorie math needs. */
    val isComplete: Boolean
        get() = birthDateOrNull != null && heightCm > 0 && weightKg > 0
}

fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(this) }.getOrNull()
