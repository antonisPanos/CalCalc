package com.example.calcalc

import com.example.calcalc.data.model.ActiveFast
import com.example.calcalc.data.model.FastingConfig
import com.example.calcalc.data.model.FastingMode
import com.example.calcalc.domain.FastingMath
import com.example.calcalc.domain.FastingStatus
import com.example.calcalc.domain.toCompactString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FastingMathTest {

    /** Fast 20:00 → 12:00, i.e. the window crosses midnight. */
    private val overnight = FastingConfig(
        mode = FastingMode.DAILY,
        dailyStartMinute = 20 * 60,
        dailyEndMinute = 12 * 60,
    )

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 9, day, hour, minute)

    @Test
    fun `inside an overnight window in the evening`() {
        val status = FastingMath.dailyStatus(overnight, at(10, 21))
        assertTrue(status is FastingStatus.Fasting)
        status as FastingStatus.Fasting
        assertEquals(at(10, 20), status.startedAt)
        assertEquals(at(11, 12), status.endsAt)
    }

    @Test
    fun `after midnight it is still yesterday's window`() {
        // 2am belongs to the fast that opened at 20:00 the previous evening.
        val status = FastingMath.dailyStatus(overnight, at(11, 2))
        assertTrue(status is FastingStatus.Fasting)
        status as FastingStatus.Fasting
        assertEquals(at(10, 20), status.startedAt)
        assertEquals(at(11, 12), status.endsAt)
    }

    @Test
    fun `midday is the eating window and the next fast is tonight`() {
        val status = FastingMath.dailyStatus(overnight, at(11, 13))
        assertTrue(status is FastingStatus.EatingWindow)
        assertEquals(at(11, 20), (status as FastingStatus.EatingWindow).nextFastStartsAt)
    }

    @Test
    fun `the boundary minute belongs to the fast, the end minute does not`() {
        assertTrue(FastingMath.dailyStatus(overnight, at(10, 20, 0)) is FastingStatus.Fasting)
        assertTrue(FastingMath.dailyStatus(overnight, at(11, 12, 0)) is FastingStatus.EatingWindow)
        assertTrue(FastingMath.dailyStatus(overnight, at(11, 11, 59)) is FastingStatus.Fasting)
    }

    @Test
    fun `a same-day window does not cross midnight`() {
        val daytime = overnight.copy(dailyStartMinute = 8 * 60, dailyEndMinute = 16 * 60)

        assertTrue(FastingMath.dailyStatus(daytime, at(10, 9)) is FastingStatus.Fasting)
        val evening = FastingMath.dailyStatus(daytime, at(10, 18))
        assertTrue(evening is FastingStatus.EatingWindow)
        // Already finished today, so the next one is tomorrow morning.
        assertEquals(at(11, 8), (evening as FastingStatus.EatingWindow).nextFastStartsAt)

        val earlyMorning = FastingMath.dailyStatus(daytime, at(10, 6))
        assertEquals(at(10, 8), (earlyMorning as FastingStatus.EatingWindow).nextFastStartsAt)
    }

    @Test
    fun `daily fast duration handles both directions`() {
        assertEquals(16L, FastingMath.dailyFastDuration(overnight).toHours())
        val daytime = overnight.copy(dailyStartMinute = 8 * 60, dailyEndMinute = 16 * 60)
        assertEquals(8L, FastingMath.dailyFastDuration(daytime).toHours())
    }

    @Test
    fun `an empty window is not a fast`() {
        val zero = overnight.copy(dailyStartMinute = 9 * 60, dailyEndMinute = 9 * 60)
        assertTrue(FastingMath.dailyStatus(zero, at(10, 9)) is FastingStatus.Idle)
    }

    @Test
    fun `timer mode reports progress and remaining time`() {
        val zone = ZoneId.of("UTC")
        val start = at(10, 20).atZone(zone).toInstant().toEpochMilli()
        val end = at(11, 12).atZone(zone).toInstant().toEpochMilli()
        val config = FastingConfig(mode = FastingMode.TIMER)

        val status = FastingMath.status(
            config = config,
            active = ActiveFast(startedAt = start, plannedEndAt = end),
            now = at(11, 4),
            zone = zone,
        )

        status as FastingStatus.Fasting
        assertEquals(8L, status.elapsed.toHours())
        assertEquals(8L, status.remaining.toHours())
        assertEquals(0.5f, status.progress, 0.001f)
        assertFalse(status.isPastGoal)
    }

    @Test
    fun `past the goal clamps rather than counting negative`() {
        val zone = ZoneId.of("UTC")
        val config = FastingConfig(mode = FastingMode.TIMER)
        val status = FastingMath.status(
            config = config,
            active = ActiveFast(
                startedAt = at(10, 20).atZone(zone).toInstant().toEpochMilli(),
                plannedEndAt = at(11, 4).atZone(zone).toInstant().toEpochMilli(),
            ),
            now = at(11, 9),
            zone = zone,
        )

        status as FastingStatus.Fasting
        assertTrue(status.isPastGoal)
        assertEquals(0L, status.remaining.seconds)
        assertEquals(1f, status.progress, 0.001f)
    }

    @Test
    fun `timer mode with nothing running is idle, and off is disabled`() {
        assertEquals(
            FastingStatus.Idle,
            FastingMath.status(FastingConfig(mode = FastingMode.TIMER), null, at(10, 9)),
        )
        assertEquals(
            FastingStatus.Disabled,
            FastingMath.status(FastingConfig(mode = FastingMode.OFF), null, at(10, 9)),
        )
    }

    @Test
    fun `compact duration formatting`() {
        assertEquals("16h 30m", java.time.Duration.ofMinutes(990).toCompactString())
        assertEquals("45m", java.time.Duration.ofMinutes(45).toCompactString())
        assertEquals("0m", java.time.Duration.ZERO.toCompactString())
    }
}
