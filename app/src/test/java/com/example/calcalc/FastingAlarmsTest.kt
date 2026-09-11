package com.example.calcalc

import com.example.calcalc.data.model.ActiveFast
import com.example.calcalc.data.model.FastingConfig
import com.example.calcalc.data.model.FastingMode
import com.example.calcalc.domain.FastingAlarm
import com.example.calcalc.domain.FastingAlarms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class FastingAlarmsTest {

    private val zone: ZoneId = ZoneId.of("Europe/Athens")

    /** 20:00 → 12:00, the usual 16:8. */
    private val daily = FastingConfig(
        mode = FastingMode.DAILY,
        dailyStartMinute = 20 * 60,
        dailyEndMinute = 12 * 60,
    )

    private fun at(day: Int, hour: Int, minute: Int = 0): LocalDateTime =
        LocalDateTime.of(LocalDate.of(2026, 9, day), java.time.LocalTime.of(hour, minute))

    private fun epoch(time: LocalDateTime): Long = time.atZone(zone).toInstant().toEpochMilli()

    private fun next(config: FastingConfig, active: ActiveFast? = null, now: LocalDateTime) =
        FastingAlarms.next(config, active, now, zone).associate { it.kind to it.atEpochMillis }

    @Test
    fun `fasting off schedules nothing`() {
        assertTrue(FastingAlarms.next(FastingConfig(), null, at(11, 10), zone).isEmpty())
    }

    @Test
    fun `a zero-length daily window schedules nothing`() {
        val config = daily.copy(dailyStartMinute = 9 * 60, dailyEndMinute = 9 * 60)

        assertTrue(FastingAlarms.next(config, null, at(11, 10), zone).isEmpty())
    }

    @Test
    fun `daily mode warns before both ends of the window`() {
        val alarms = next(daily, now = at(11, 10))

        assertEquals(epoch(at(11, 19, 45)), alarms[FastingAlarm.Kind.FAST_STARTING_SOON])
        assertEquals(epoch(at(11, 11, 45)), alarms[FastingAlarm.Kind.FAST_ENDING_SOON])
    }

    @Test
    fun `a warning whose moment has passed rolls to tomorrow`() {
        // 19:50 is already inside the fifteen-minute lead, so warning now would be pointless.
        val alarms = next(daily, now = at(11, 19, 50))

        assertEquals(epoch(at(12, 19, 45)), alarms[FastingAlarm.Kind.FAST_STARTING_SOON])
    }

    @Test
    fun `after midnight the next end-of-fast warning is still later the same morning`() {
        val alarms = next(daily, now = at(12, 2))

        assertEquals(epoch(at(12, 11, 45)), alarms[FastingAlarm.Kind.FAST_ENDING_SOON])
        assertEquals(epoch(at(12, 19, 45)), alarms[FastingAlarm.Kind.FAST_STARTING_SOON])
    }

    @Test
    fun `a window that starts just after midnight is not pushed a day back by the lead`() {
        // 00:10 minus fifteen minutes is yesterday; naive arithmetic would skip a whole day.
        val config = daily.copy(dailyStartMinute = 10, dailyEndMinute = 8 * 60)
        val alarms = next(config, now = at(11, 23, 0))

        assertEquals(epoch(at(11, 23, 55)), alarms[FastingAlarm.Kind.FAST_STARTING_SOON])
    }

    @Test
    fun `timer mode warns before the running fast ends`() {
        val now = at(11, 10)
        val active = ActiveFast(
            startedAt = epoch(at(11, 8)),
            plannedEndAt = epoch(at(11, 16)),
        )

        val alarms = next(daily.copy(mode = FastingMode.TIMER), active, now)

        assertEquals(1, alarms.size)
        assertEquals(epoch(at(11, 15, 45)), alarms[FastingAlarm.Kind.FAST_ENDING_SOON])
    }

    @Test
    fun `timer mode with nothing running schedules nothing`() {
        assertTrue(
            FastingAlarms.next(daily.copy(mode = FastingMode.TIMER), null, at(11, 10), zone).isEmpty()
        )
    }

    @Test
    fun `a fast already inside the lead gets no warning`() {
        val active = ActiveFast(
            startedAt = epoch(at(11, 8)),
            plannedEndAt = epoch(at(11, 10, 5)),
        )

        assertTrue(
            FastingAlarms
                .next(daily.copy(mode = FastingMode.TIMER), active, at(11, 10), zone)
                .isEmpty()
        )
    }
}
