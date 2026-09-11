package com.example.calcalc.domain

import com.example.calcalc.data.model.FoodItem
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Where the day is, and roughly how much of the daily budget a normal eating pattern has
 * used by then. The fractions are the whole point of this file: judging a day at 09:00
 * against the full daily target would call every breakfast a failure.
 */
enum class DayPhase(val expectedIntakeFraction: Double) {
    MORNING(0.25),
    MIDDAY(0.55),
    AFTERNOON(0.70),
    EVENING(0.95),
    NIGHT(1.0);

    /** Before this point the day is too young to complain about what is missing. */
    val isLateEnoughToJudge: Boolean get() = ordinal >= AFTERNOON.ordinal

    companion object {
        fun of(time: LocalTime): DayPhase = when (time.hour) {
            in 0..3 -> NIGHT // The small hours belong to the night before.
            in 4..10 -> MORNING
            in 11..14 -> MIDDAY
            in 15..17 -> AFTERNOON
            in 18..21 -> EVENING
            else -> NIGHT
        }
    }
}

enum class InsightTone { GOOD, NEUTRAL, WARNING }

data class InsightLine(val text: String, val tone: InsightTone)

data class DayInsight(val phase: DayPhase, val lines: List<InsightLine>)

/**
 * A short, time-aware read on how the day is going, shown on Home once something has been
 * logged. Deliberately pure so the wording rules can be tested without a device.
 */
object NutritionInsight {

    /** Grams of protein per kg of bodyweight to aim for. */
    const val PROTEIN_G_PER_KG = 1.6

    /** Fallback when no weight is known: a quarter of the calorie budget from protein. */
    private const val PROTEIN_FRACTION_OF_KCAL = 0.25

    /** Below this share of calories carrying macro data, macro advice would be guesswork. */
    private const val MIN_MACRO_COVERAGE = 0.6

    private const val FAT_HEAVY_FRACTION = 0.45
    private const val CARB_HEAVY_FRACTION = 0.65

    fun evaluate(
        items: List<FoodItem>,
        targetKcal: Int,
        weightKg: Double?,
        time: LocalTime = LocalTime.now(),
    ): DayInsight? {
        if (items.isEmpty()) return null
        val phase = DayPhase.of(time)
        val consumed = items.sumOf { it.calories }

        val lines = buildList {
            paceLine(consumed, targetKcal, phase)?.let(::add)
            proteinLine(items, consumed, targetKcal, weightKg, phase)?.let(::add)
            balanceLine(items, consumed, phase)?.let(::add)
        }
        return DayInsight(phase = phase, lines = lines)
    }

    /** Calories so far against what this hour of the day usually accounts for. */
    private fun paceLine(consumed: Int, targetKcal: Int, phase: DayPhase): InsightLine? {
        if (targetKcal <= 0) return null
        val expected = (targetKcal * phase.expectedIntakeFraction).roundToInt()
        val remaining = targetKcal - consumed

        return when {
            consumed > targetKcal -> InsightLine(
                "${consumed - targetKcal} kcal over the day's target.",
                InsightTone.WARNING,
            )

            consumed > expected * 1.25 -> InsightLine(
                if (phase.isLateEnoughToJudge) {
                    "Ahead of pace — $remaining kcal left for the rest of the day."
                } else {
                    // A big breakfast is a choice, not a mistake; just state the arithmetic.
                    "A big start: $consumed kcal in, $remaining left for the day."
                },
                if (phase.isLateEnoughToJudge) InsightTone.WARNING else InsightTone.NEUTRAL,
            )

            consumed < expected * 0.6 -> InsightLine(
                "Light so far — $consumed kcal, with $remaining still to go.",
                InsightTone.NEUTRAL,
            )

            else -> InsightLine(
                "On pace: $consumed of $targetKcal kcal, $remaining left.",
                InsightTone.GOOD,
            )
        }
    }

    /**
     * Protein is the one macro worth chasing on a deficit, so it gets its own line — but
     * only ever as a complaint once the day is far enough along to have earned it.
     */
    private fun proteinLine(
        items: List<FoodItem>,
        consumed: Int,
        targetKcal: Int,
        weightKg: Double?,
        phase: DayPhase,
    ): InsightLine? {
        if (macroCoverage(items, consumed) { it.proteinG } < MIN_MACRO_COVERAGE) return null

        val goal = proteinGoalG(targetKcal, weightKg) ?: return null
        val protein = items.sumOf { it.proteinG ?: 0.0 }
        val expected = goal * phase.expectedIntakeFraction

        return when {
            protein >= expected -> InsightLine(
                "Protein on track: ${protein.roundToInt()} g of ~${goal.roundToInt()} g.",
                InsightTone.GOOD,
            )

            !phase.isLateEnoughToJudge -> null

            else -> InsightLine(
                "Protein is light: ${protein.roundToInt()} g so far, aiming for " +
                    "~${goal.roundToInt()} g today.",
                InsightTone.NEUTRAL,
            )
        }
    }

    /** Fat- or carb-dominance only becomes a fair read once most of the day is in. */
    private fun balanceLine(items: List<FoodItem>, consumed: Int, phase: DayPhase): InsightLine? {
        if (phase.ordinal < DayPhase.EVENING.ordinal) return null
        if (consumed <= 0) return null
        if (macroCoverage(items, consumed) { it.fatG } < MIN_MACRO_COVERAGE) return null
        if (macroCoverage(items, consumed) { it.carbsG } < MIN_MACRO_COVERAGE) return null

        val fatKcal = items.sumOf { (it.fatG ?: 0.0) * 9 }
        val carbKcal = items.sumOf { (it.carbsG ?: 0.0) * 4 }

        return when {
            fatKcal / consumed > FAT_HEAVY_FRACTION -> InsightLine(
                "Fat-heavy day — ${(100 * fatKcal / consumed).roundToInt()}% of calories from fat.",
                InsightTone.NEUTRAL,
            )

            carbKcal / consumed > CARB_HEAVY_FRACTION -> InsightLine(
                "Carb-heavy day — ${(100 * carbKcal / consumed).roundToInt()}% of calories from carbs.",
                InsightTone.NEUTRAL,
            )

            else -> null
        }
    }

    fun proteinGoalG(targetKcal: Int, weightKg: Double?): Double? = when {
        weightKg != null && weightKg > 0 -> weightKg * PROTEIN_G_PER_KG
        targetKcal > 0 -> targetKcal * PROTEIN_FRACTION_OF_KCAL / 4
        else -> null
    }

    /**
     * Share of today's calories that came with the macro in question. Gemini leaves macros
     * null when it cannot estimate them, and averaging over the half of the day that has
     * numbers would understate every total.
     */
    private inline fun macroCoverage(
        items: List<FoodItem>,
        consumed: Int,
        macro: (FoodItem) -> Double?,
    ): Double {
        if (consumed <= 0) return 0.0
        val covered = items.filter { macro(it) != null }.sumOf { it.calories }
        return covered.toDouble() / consumed
    }
}
