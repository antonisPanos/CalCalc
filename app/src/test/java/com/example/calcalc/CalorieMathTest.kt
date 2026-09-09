package com.example.calcalc

import com.example.calcalc.data.model.ActivityLevel
import com.example.calcalc.data.model.Goal
import com.example.calcalc.data.model.Sex
import com.example.calcalc.data.model.UserProfile
import com.example.calcalc.domain.CalorieMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalorieMathTest {

    private val today = LocalDate.of(2026, 1, 1)

    /** 80 kg, 180 cm, 30 y male, sedentary. */
    private val maleProfile = UserProfile(
        sex = Sex.MALE,
        birthDate = "1996-01-01",
        heightCm = 180.0,
        weightKg = 80.0,
        activityLevel = ActivityLevel.SEDENTARY,
        goal = Goal.MAINTAIN,
    )

    @Test
    fun `bmr matches Mifflin-St Jeor by hand`() {
        // 10*80 + 6.25*180 - 5*30 + 5
        assertEquals(1780.0, CalorieMath.bmr(Sex.MALE, 80.0, 180.0, 30), 0.001)
        // Same body, female constant: -161 instead of +5
        assertEquals(1614.0, CalorieMath.bmr(Sex.FEMALE, 80.0, 180.0, 30), 0.001)
    }

    @Test
    fun `tdee applies the activity multiplier`() {
        assertEquals(2136.0, CalorieMath.tdee(1780.0, ActivityLevel.SEDENTARY), 0.001)
        assertEquals(3382.0, CalorieMath.tdee(1780.0, ActivityLevel.ATHLETE), 0.001)
    }

    @Test
    fun `maintain means eat what you burn`() {
        val target = CalorieMath.dailyTarget(maleProfile, currentWeightKg = 80.0, today = today)!!
        assertEquals(1780, target.bmr)
        assertEquals(2136, target.tdee)
        assertEquals(2136, target.target)
        assertEquals(0, target.appliedDelta)
        assertFalse(target.isClamped)
        assertNull(target.realisticGoalDate)
    }

    @Test
    fun `an aggressive goal date is clamped and a realistic date is offered`() {
        val profile = maleProfile.copy(
            goal = Goal.LOSE,
            goalWeightKg = 70.0,
            goalDate = "2026-02-01", // 31 days for 10 kg
        )
        val target = CalorieMath.dailyTarget(profile, currentWeightKg = 80.0, today = today)!!

        // 10 kg in 31 days is about -2484 kcal/day.
        assertEquals(-2484, target.requestedDelta)
        assertTrue(target.isClamped)
        // The 1000 kcal cap would give 1136 kcal, below the male floor, so the floor wins.
        assertEquals(1500, target.target)
        assertEquals(-636, target.appliedDelta)
        assertEquals(LocalDate.of(2026, 5, 3), target.realisticGoalDate)
    }

    @Test
    fun `no goal date falls back to half a kilo per week`() {
        val profile = maleProfile.copy(goal = Goal.LOSE, goalWeightKg = 70.0, goalDate = null)
        val target = CalorieMath.dailyTarget(profile, currentWeightKg = 80.0, today = today)!!

        // 0.5 kg/week = 550 kcal/day
        assertEquals(-550, target.requestedDelta)
        assertEquals(-550, target.appliedDelta)
        assertEquals(1586, target.target)
        assertFalse(target.isClamped)
    }

    @Test
    fun `the female floor is lower than the male one`() {
        val profile = UserProfile(
            sex = Sex.FEMALE,
            birthDate = "1996-01-01",
            heightCm = 165.0,
            weightKg = 55.0,
            activityLevel = ActivityLevel.SEDENTARY,
            goal = Goal.LOSE,
            goalWeightKg = 50.0,
            goalDate = "2026-01-31",
        )
        val target = CalorieMath.dailyTarget(profile, currentWeightKg = 55.0, today = today)!!
        assertEquals(1200, target.target)
    }

    @Test
    fun `a goal date in the past does not divide by zero`() {
        val profile = maleProfile.copy(
            goal = Goal.LOSE,
            goalWeightKg = 70.0,
            goalDate = "2025-06-01",
        )
        val target = CalorieMath.dailyTarget(profile, currentWeightKg = 80.0, today = today)!!
        // Falls back to the default safe rate rather than producing an infinite deficit.
        assertEquals(-550, target.appliedDelta)
    }

    @Test
    fun `today as the goal date does not divide by zero`() {
        val profile = maleProfile.copy(
            goal = Goal.LOSE,
            goalWeightKg = 70.0,
            goalDate = today.toString(),
        )
        val target = CalorieMath.dailyTarget(profile, currentWeightKg = 80.0, today = today)!!
        assertEquals(-550, target.appliedDelta)
    }

    @Test
    fun `gaining moves the target up`() {
        val profile = maleProfile.copy(goal = Goal.GAIN, goalWeightKg = 85.0, goalDate = null)
        val target = CalorieMath.dailyTarget(profile, currentWeightKg = 80.0, today = today)!!
        assertEquals(550, target.appliedDelta)
        assertEquals(2686, target.target)
    }

    @Test
    fun `already at the goal weight needs no adjustment`() {
        val profile = maleProfile.copy(goal = Goal.LOSE, goalWeightKg = 80.0, goalDate = "2026-06-01")
        val target = CalorieMath.dailyTarget(profile, currentWeightKg = 80.0, today = today)!!
        assertEquals(0, target.appliedDelta)
        assertEquals(today, target.realisticGoalDate)
    }

    @Test
    fun `an incomplete profile yields no target`() {
        assertNull(CalorieMath.dailyTarget(UserProfile(), currentWeightKg = 80.0, today = today))
        assertNull(CalorieMath.dailyTarget(maleProfile, currentWeightKg = 0.0, today = today))
    }
}
