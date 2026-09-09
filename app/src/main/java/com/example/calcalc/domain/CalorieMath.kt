package com.example.calcalc.domain

import com.example.calcalc.data.model.ActivityLevel
import com.example.calcalc.data.model.Goal
import com.example.calcalc.data.model.Sex
import com.example.calcalc.data.model.UserProfile
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Result of turning a profile into a daily calorie budget.
 *
 * [requestedDelta] is what the user's goal date literally demands; [appliedDelta] is what
 * we actually use after clamping to a safe rate. When they differ the UI should say so and
 * offer [realisticGoalDate] instead.
 */
data class CalorieTarget(
    val bmr: Int,
    val tdee: Int,
    val target: Int,
    val requestedDelta: Int,
    val appliedDelta: Int,
    val realisticGoalDate: LocalDate?,
) {
    val isClamped: Boolean get() = requestedDelta != appliedDelta
}

object CalorieMath {

    /** Energy density of body mass. The standard planning figure, not a precise constant. */
    const val KCAL_PER_KG = 7700.0

    /** Never ask for more than a ~1 kg/week swing. */
    const val MAX_DAILY_DELTA = 1000.0

    /**
     * Hard floors on the daily budget. A BMR-relative floor is tempting but useless here:
     * for a sedentary person TDEE is only 1.2x BMR, so any floor near BMR would cancel out
     * almost the whole deficit. These are the conventional minimum intakes instead.
     */
    const val MIN_DAILY_KCAL_MALE = 1500.0
    const val MIN_DAILY_KCAL_FEMALE = 1200.0

    /** Rate used when the user wants to lose/gain but set no deadline: 0.5 kg/week. */
    const val DEFAULT_WEEKLY_KG = 0.5

    fun ageOn(birthDate: LocalDate, today: LocalDate): Int =
        ChronoUnit.YEARS.between(birthDate, today).toInt()

    /** Mifflin-St Jeor. */
    fun bmr(sex: Sex, weightKg: Double, heightCm: Double, age: Int): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
        return if (sex == Sex.MALE) base + 5.0 else base - 161.0
    }

    fun tdee(bmr: Double, activityLevel: ActivityLevel): Double = bmr * activityLevel.multiplier

    /**
     * @param currentWeightKg latest logged weight, falling back to the profile's starting weight
     * @return null when the profile is not complete enough to compute anything
     */
    fun dailyTarget(
        profile: UserProfile,
        currentWeightKg: Double,
        today: LocalDate = LocalDate.now(),
    ): CalorieTarget? {
        val birthDate = profile.birthDateOrNull ?: return null
        if (profile.heightCm <= 0 || currentWeightKg <= 0) return null

        val bmr = bmr(profile.sex, currentWeightKg, profile.heightCm, ageOn(birthDate, today))
        val tdee = tdee(bmr, profile.activityLevel)

        val requested = requestedDelta(profile, currentWeightKg, today)
        val capped = requested.coerceIn(-MAX_DAILY_DELTA, MAX_DAILY_DELTA)

        val floor = minimumIntake(profile.sex)
        val target = maxOf(tdee + capped, floor)
        // The floor can eat into the deficit, so the delta we can actually deliver is
        // whatever survived it.
        val applied = target - tdee

        return CalorieTarget(
            bmr = bmr.roundToInt(),
            tdee = tdee.roundToInt(),
            target = target.roundToInt(),
            requestedDelta = requested.roundToInt(),
            appliedDelta = applied.roundToInt(),
            realisticGoalDate = projectedGoalDate(profile, currentWeightKg, applied, today),
        )
    }

    fun minimumIntake(sex: Sex): Double =
        if (sex == Sex.MALE) MIN_DAILY_KCAL_MALE else MIN_DAILY_KCAL_FEMALE

    private fun requestedDelta(
        profile: UserProfile,
        currentWeightKg: Double,
        today: LocalDate,
    ): Double {
        if (profile.goal == Goal.MAINTAIN) return 0.0
        val goalWeight = profile.goalWeightKg ?: return 0.0
        val kgToGo = goalWeight - currentWeightKg
        if (abs(kgToGo) < 0.05) return 0.0

        val goalDate = profile.goalDateOrNull
        val days = goalDate?.let { ChronoUnit.DAYS.between(today, it) } ?: 0L
        return if (days > 0) {
            kgToGo * KCAL_PER_KG / days
        } else {
            // No deadline, or a deadline already in the past: fall back to the default
            // safe rate in the right direction.
            val sign = if (kgToGo > 0) 1.0 else -1.0
            sign * DEFAULT_WEEKLY_KG * KCAL_PER_KG / 7.0
        }
    }

    /** When the goal weight is reached at [appliedDelta] kcal/day. Null if never (or already there). */
    private fun projectedGoalDate(
        profile: UserProfile,
        currentWeightKg: Double,
        appliedDelta: Double,
        today: LocalDate,
    ): LocalDate? {
        if (profile.goal == Goal.MAINTAIN) return null
        val goalWeight = profile.goalWeightKg ?: return null
        val kgToGo = goalWeight - currentWeightKg
        if (abs(kgToGo) < 0.05) return today
        // Moving the wrong way (or not at all) never reaches the goal.
        if (appliedDelta == 0.0 || (kgToGo > 0) != (appliedDelta > 0)) return null

        val days = ceil(abs(kgToGo) * KCAL_PER_KG / abs(appliedDelta)).toLong()
        return if (days > 365L * 20) null else today.plusDays(days)
    }
}
