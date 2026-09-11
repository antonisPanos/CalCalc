package com.example.calcalc.domain

import com.example.calcalc.data.model.ActiveFast
import com.example.calcalc.data.model.FastingConfig
import com.example.calcalc.data.model.FastingMode
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/** A single heads-up to schedule. [atEpochMillis] is absolute so the alarm survives a reboot. */
data class FastingAlarm(val kind: Kind, val atEpochMillis: Long) {
    enum class Kind {
        /** The eating window is about to close. */
        FAST_STARTING_SOON,

        /** The fast is nearly over — food is coming. */
        FAST_ENDING_SOON,
    }
}

/**
 * Works out the next heads-up for each kind. Kept pure and separate from AlarmManager so
 * the midnight-crossing cases can be tested, which is where every schedule bug lives.
 */
object FastingAlarms {

    /** How far ahead of the event to warn. */
    val DEFAULT_LEAD: Duration = Duration.ofMinutes(15)

    fun next(
        config: FastingConfig,
        active: ActiveFast?,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        lead: Duration = DEFAULT_LEAD,
    ): List<FastingAlarm> = when (config.mode) {
        FastingMode.OFF -> emptyList()
        // A timer fast has no scheduled start — the user presses the button — so only the
        // end is predictable.
        FastingMode.TIMER -> timerAlarms(active, now, zone, lead)
        FastingMode.DAILY -> dailyAlarms(config, now, zone, lead)
    }

    private fun timerAlarms(
        active: ActiveFast?,
        now: LocalDateTime,
        zone: ZoneId,
        lead: Duration,
    ): List<FastingAlarm> {
        if (active == null || !active.isSet) return emptyList()
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val at = active.plannedEndAt - lead.toMillis()
        return if (at > nowMillis) {
            listOf(FastingAlarm(FastingAlarm.Kind.FAST_ENDING_SOON, at))
        } else {
            emptyList()
        }
    }

    private fun dailyAlarms(
        config: FastingConfig,
        now: LocalDateTime,
        zone: ZoneId,
        lead: Duration,
    ): List<FastingAlarm> {
        if (!config.hasValidDailyWindow) return emptyList()
        return listOf(
            FastingAlarm(
                FastingAlarm.Kind.FAST_STARTING_SOON,
                nextOccurrence(config.dailyStartMinute, now, zone, lead),
            ),
            FastingAlarm(
                FastingAlarm.Kind.FAST_ENDING_SOON,
                nextOccurrence(config.dailyEndMinute, now, zone, lead),
            ),
        )
    }

    /**
     * The next time the wall clock reads [minuteOfDay], minus [lead], strictly in the
     * future. Subtracting the lead first would be wrong: 00:10 minus 15 minutes lands on
     * yesterday, and the alarm would be skipped for a whole day.
     */
    private fun nextOccurrence(
        minuteOfDay: Int,
        now: LocalDateTime,
        zone: ZoneId,
        lead: Duration,
    ): Long {
        var fireAt = now.toLocalDate().atStartOfDay()
            .plusMinutes(minuteOfDay.toLong())
            .minus(lead)
        if (!fireAt.isAfter(now)) fireAt = fireAt.plusDays(1)
        return fireAt.atZone(zone).toInstant().toEpochMilli()
    }
}
