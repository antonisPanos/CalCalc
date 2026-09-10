package com.example.calcalc.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * A clock that recomposes its readers once per [periodMillis].
 *
 * Countdowns are always derived from stored timestamps against this value rather than
 * decremented in memory, so they stay correct across process death and clock changes.
 */
@Composable
fun rememberTickingNow(periodMillis: Long = 1_000L): State<LocalDateTime> =
    produceState(initialValue = LocalDateTime.now(), periodMillis) {
        while (true) {
            value = LocalDateTime.now()
            delay(periodMillis)
        }
    }
