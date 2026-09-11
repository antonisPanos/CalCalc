package com.example.calcalc

import com.example.calcalc.data.model.FoodItem
import com.example.calcalc.domain.DayPhase
import com.example.calcalc.domain.InsightTone
import com.example.calcalc.domain.NutritionInsight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class NutritionInsightTest {

    private fun item(
        calories: Int,
        protein: Double? = null,
        carbs: Double? = null,
        fat: Double? = null,
    ) = FoodItem(
        name = "Food",
        quantity = "1",
        calories = calories,
        proteinG = protein,
        carbsG = carbs,
        fatG = fat,
    )

    private fun lines(
        items: List<FoodItem>,
        targetKcal: Int = 2000,
        weightKg: Double? = 80.0,
        time: LocalTime,
    ) = NutritionInsight.evaluate(items, targetKcal, weightKg, time)?.lines.orEmpty()

    @Test
    fun `nothing logged means nothing to say`() {
        assertNull(NutritionInsight.evaluate(emptyList(), 2000, 80.0, LocalTime.of(9, 0)))
    }

    @Test
    fun `the small hours count as the night before`() {
        assertEquals(DayPhase.NIGHT, DayPhase.of(LocalTime.of(2, 0)))
        assertEquals(DayPhase.MORNING, DayPhase.of(LocalTime.of(8, 0)))
        assertEquals(DayPhase.EVENING, DayPhase.of(LocalTime.of(20, 0)))
    }

    @Test
    fun `a breakfast-only morning is never told what it is missing`() {
        val result = lines(
            items = listOf(item(calories = 400, protein = 10.0)),
            time = LocalTime.of(8, 30),
        )

        assertTrue(result.none { it.text.contains("Protein is light") })
        assertTrue(result.none { it.tone == InsightTone.WARNING })
    }

    @Test
    fun `a big breakfast is stated, not scolded`() {
        val result = lines(items = listOf(item(calories = 900)), time = LocalTime.of(9, 0))

        val pace = result.first()
        assertTrue(pace.text.startsWith("A big start"))
        assertEquals(InsightTone.NEUTRAL, pace.tone)
    }

    @Test
    fun `the same overshoot in the afternoon is a warning`() {
        val result = lines(items = listOf(item(calories = 1900)), time = LocalTime.of(16, 0))

        val pace = result.first()
        assertTrue(pace.text.startsWith("Ahead of pace"))
        assertEquals(InsightTone.WARNING, pace.tone)
    }

    @Test
    fun `protein shortfall surfaces once the day is far enough along`() {
        val morning = lines(
            items = listOf(item(calories = 1200, protein = 30.0)),
            time = LocalTime.of(9, 0),
        )
        val afternoon = lines(
            items = listOf(item(calories = 1200, protein = 30.0)),
            time = LocalTime.of(16, 0),
        )

        assertTrue(morning.none { it.text.contains("Protein is light") })
        assertTrue(afternoon.any { it.text.contains("Protein is light") })
    }

    @Test
    fun `protein on track is praised at any hour`() {
        val result = lines(
            items = listOf(item(calories = 500, protein = 45.0)),
            time = LocalTime.of(9, 0),
        )

        assertTrue(result.any { it.text.startsWith("Protein on track") && it.tone == InsightTone.GOOD })
    }

    @Test
    fun `no macro data means no macro advice`() {
        val result = lines(items = listOf(item(calories = 1200)), time = LocalTime.of(16, 0))

        assertTrue(result.none { it.text.contains("Protein") })
    }

    @Test
    fun `partial macro coverage is not extrapolated`() {
        // Two thirds of the day's calories carry no protein figure, so the total would be
        // a lie — say nothing rather than invent a shortfall.
        val result = lines(
            items = listOf(item(calories = 400, protein = 35.0), item(calories = 800)),
            time = LocalTime.of(16, 0),
        )

        assertTrue(result.none { it.text.contains("Protein") })
    }

    @Test
    fun `going over the target is called out`() {
        val result = lines(items = listOf(item(calories = 2200)), time = LocalTime.of(20, 0))

        val pace = result.first()
        assertEquals("200 kcal over the day's target.", pace.text)
        assertEquals(InsightTone.WARNING, pace.tone)
    }

    @Test
    fun `a fat-heavy day is only judged in the evening`() {
        val heavy = listOf(item(calories = 1000, protein = 50.0, carbs = 50.0, fat = 60.0))

        assertTrue(lines(heavy, time = LocalTime.of(16, 0)).none { it.text.contains("Fat-heavy") })
        assertTrue(lines(heavy, time = LocalTime.of(20, 0)).any { it.text.contains("Fat-heavy") })
    }

    @Test
    fun `protein goal falls back to a share of calories when weight is unknown`() {
        assertEquals(128.0, NutritionInsight.proteinGoalG(2000, 80.0)!!, 0.01)
        assertEquals(125.0, NutritionInsight.proteinGoalG(2000, null)!!, 0.01)
        assertNull(NutritionInsight.proteinGoalG(0, null))
    }

    @Test
    fun `no target means no pace line`() {
        val result = lines(
            items = listOf(item(calories = 500, protein = 40.0)),
            targetKcal = 0,
            time = LocalTime.of(12, 0),
        )

        assertTrue(result.none { it.text.contains("kcal") })
    }
}
