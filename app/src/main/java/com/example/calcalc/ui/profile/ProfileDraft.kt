package com.example.calcalc.ui.profile

import com.example.calcalc.data.model.ActivityLevel
import com.example.calcalc.data.model.Goal
import com.example.calcalc.data.model.Sex
import com.example.calcalc.data.model.UserProfile
import java.time.LocalDate

/**
 * Editable form state. Numbers stay as strings while typing so a partially typed value is
 * not rewritten under the cursor; conversion happens only on save.
 *
 * Shared by onboarding and the profile screen — the same questions, asked twice.
 */
data class ProfileDraft(
    val sex: Sex = Sex.MALE,
    val birthDate: LocalDate? = null,
    val heightCm: String = "",
    val weightKg: String = "",
    val activityLevel: ActivityLevel = ActivityLevel.SEDENTARY,
    val goal: Goal = Goal.MAINTAIN,
    val goalWeightKg: String = "",
    val goalDate: LocalDate? = null,
) {
    val height: Double? get() = heightCm.toDoubleOrNull()?.takeIf { it in 80.0..260.0 }
    val weight: Double? get() = weightKg.toDoubleOrNull()?.takeIf { it in 25.0..400.0 }
    val goalWeight: Double? get() = goalWeightKg.toDoubleOrNull()?.takeIf { it in 25.0..400.0 }

    val basicsValid: Boolean get() = birthDate != null && height != null && weight != null

    /** A weight goal is only actionable with a target weight to aim at. */
    val goalValid: Boolean get() = goal == Goal.MAINTAIN || goalWeight != null

    val isValid: Boolean get() = basicsValid && goalValid

    fun toProfile(existing: UserProfile?): UserProfile = UserProfile(
        displayName = existing?.displayName.orEmpty(),
        sex = sex,
        birthDate = birthDate?.toString().orEmpty(),
        heightCm = height ?: 0.0,
        weightKg = weight ?: 0.0,
        activityLevel = activityLevel,
        goal = goal,
        goalWeightKg = if (goal == Goal.MAINTAIN) null else goalWeight,
        goalDate = if (goal == Goal.MAINTAIN) null else goalDate?.toString(),
    )

    companion object {
        fun from(profile: UserProfile?): ProfileDraft {
            if (profile == null) return ProfileDraft()
            return ProfileDraft(
                sex = profile.sex,
                birthDate = profile.birthDateOrNull,
                heightCm = profile.heightCm.takeIf { it > 0 }?.trimmed().orEmpty(),
                weightKg = profile.weightKg.takeIf { it > 0 }?.trimmed().orEmpty(),
                activityLevel = profile.activityLevel,
                goal = profile.goal,
                goalWeightKg = profile.goalWeightKg?.trimmed().orEmpty(),
                goalDate = profile.goalDateOrNull,
            )
        }
    }
}

fun Double.trimmed(): String = if (this == toLong().toDouble()) toLong().toString() else toString()

val Goal.label: String
    get() = when (this) {
        Goal.MAINTAIN -> "Maintain"
        Goal.LOSE -> "Lose"
        Goal.GAIN -> "Gain"
    }

val Sex.label: String get() = if (this == Sex.MALE) "Male" else "Female"
