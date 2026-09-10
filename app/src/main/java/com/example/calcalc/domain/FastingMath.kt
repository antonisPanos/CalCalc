package com.example.calcalc.domain

import com.example.calcalc.data.model.ActiveFast
import com.example.calcalc.data.model.FastingConfig
import com.example.calcalc.data.model.FastingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

sealed interface FastingStatus {

    /** Fasting is switched off entirely. */
    data object Disabled : FastingStatus

    /** Timer mode with nothing running. */
    data object Idle : FastingStatus

    data class Fasting(
        val startedAt: LocalDateTime,
        val endsAt: LocalDateTime,
        val now: LocalDateTime,
    ) : FastingStatus {
        val elapsed: Duration get() = Duration.between(startedAt, now)
        val total: Duration get() = Duration.between(startedAt, endsAt)

        /** Never negative: past the goal this reads as zero remaining, not a countdown up. */
        val remaining: Duration
            get() = Duration.between(now, endsAt).let { if (it.isNegative) Duration.ZERO else it }

        val isPastGoal: Boolean get() = !now.isBefore(endsAt)

        val progress: Float
            get() {
                val totalSeconds = total.seconds
                if (totalSeconds <= 0) return 1f
                return (elapsed.seconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
            }
    }

    /** Daily mode, currently inside the eating window. */
    data class EatingWindow(
        val nextFastStartsAt: LocalDateTime,
        val now: LocalDateTime,
    ) : FastingStatus {
        val untilNextFast: Duration get() = Duration.between(now, nextFastStartsAt)
    }
}

object FastingMath {

    val PRESET_HOURS = listOf(8, 16, 24)

    fun status(
        config: FastingConfig,
        active: ActiveFast?,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): FastingStatus = when (config.mode) {
        FastingMode.OFF -> FastingStatus.Disabled
        FastingMode.TIMER -> timerStatus(active, now, zone)
        FastingMode.DAILY -> dailyStatus(config, now)
    }

    private fun timerStatus(active: ActiveFast?, now: LocalDateTime, zone: ZoneId): FastingStatus {
        if (active == null || !active.isSet) return FastingStatus.Idle
        return FastingStatus.Fasting(
            startedAt = active.startedAt.toLocalDateTime(zone),
            endsAt = active.plannedEndAt.toLocalDateTime(zone),
            now = now,
        )
    }

    /**
     * A daily window is a wall-clock rule, so the concrete fast has to be anchored to a
     * date. Two candidates matter: the window that opened today and the one that opened
     * yesterday — the latter is what you are inside of at 2am on a 20:00 → 12:00 schedule.
     */
    fun dailyStatus(config: FastingConfig, now: LocalDateTime): FastingStatus {
        if (!config.hasValidDailyWindow) return FastingStatus.Idle

        val startToday = now.toLocalDate().atStartOfDay().plusMinutes(config.dailyStartMinute.toLong())
        val endOfTodaysWindow = endFor(startToday, config)

        if (!now.isBefore(startToday) && now.isBefore(endOfTodaysWindow)) {
            return FastingStatus.Fasting(startToday, endOfTodaysWindow, now)
        }

        val startYesterday = startToday.minusDays(1)
        val endOfYesterdaysWindow = endFor(startYesterday, config)
        if (!now.isBefore(startYesterday) && now.isBefore(endOfYesterdaysWindow)) {
            return FastingStatus.Fasting(startYesterday, endOfYesterdaysWindow, now)
        }

        // Between windows: the next fast is today's if it hasn't opened yet, else tomorrow's.
        val nextStart = if (now.isBefore(startToday)) startToday else startToday.plusDays(1)
        return FastingStatus.EatingWindow(nextFastStartsAt = nextStart, now = now)
    }

    /** Rolls the end into the next day when the window crosses midnight. */
    private fun endFor(start: LocalDateTime, config: FastingConfig): LocalDateTime {
        val endSameDay = start.toLocalDate().atStartOfDay().plusMinutes(config.dailyEndMinute.toLong())
        return if (config.dailyEndMinute > config.dailyStartMinute) endSameDay else endSameDay.plusDays(1)
    }

    /** Length of the daily fast, for display next to the window itself. */
    fun dailyFastDuration(config: FastingConfig): Duration {
        val raw = config.dailyEndMinute - config.dailyStartMinute
        val minutes = if (raw > 0) raw else raw + 24 * 60
        return Duration.ofMinutes(minutes.toLong())
    }

    private fun Long.toLocalDateTime(zone: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)
}

/** "16h 30m", or "45m" when under an hour. */
fun Duration.toCompactString(): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
