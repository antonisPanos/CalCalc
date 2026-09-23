package com.example.calcalc.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * A clock that recomposes its readers once per [periodMillis].
 *
 * Countdowns are always derived from stored timestamps against this value rather than
 * decremented in memory, so they stay correct across process death and clock changes.
 *
 * Ticking stops while the screen is in the background and restarts with a fresh reading on
 * resume, so an app left in memory overnight never comes back showing yesterday.
 */
@Composable
fun rememberTickingNow(periodMillis: Long = 1_000L): State<LocalDateTime> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(initialValue = LocalDateTime.now(), periodMillis, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = LocalDateTime.now()
                delay(periodMillis)
            }
        }
    }
}
