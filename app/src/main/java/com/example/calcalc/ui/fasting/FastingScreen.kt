package com.example.calcalc.ui.fasting

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calcalc.data.model.FastRecord
import com.example.calcalc.data.model.FastingMode
import com.example.calcalc.domain.FastingMath
import com.example.calcalc.domain.FastingStatus
import com.example.calcalc.domain.toCompactString
import com.example.calcalc.notify.FastingNotifications
import com.example.calcalc.notify.FastingScheduler
import com.example.calcalc.ui.SessionViewModel
import com.example.calcalc.ui.components.DecimalField
import com.example.calcalc.ui.components.FastDialRow
import com.example.calcalc.ui.components.RingGauge
import com.example.calcalc.ui.components.ScreenScaffold
import com.example.calcalc.ui.components.SegmentedChoice
import com.example.calcalc.ui.components.TimeField
import com.example.calcalc.ui.components.rememberTickingNow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private val CLOCK_WITH_DAY = DateTimeFormatter.ofPattern("EEE HH:mm")

@Composable
fun FastingScreen(
    session: SessionViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FastingViewModel = viewModel(factory = FastingViewModel.Factory),
) {
    val config by session.fastingConfig.collectAsStateWithLifecycle()
    val activeFast by session.activeFast.collectAsStateWithLifecycle()
    val recentFasts by viewModel.recentFasts.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val now by rememberTickingNow()

    val status = FastingMath.status(config, activeFast, now)

    ScreenScaffold(title = "Fasting", onBack = onBack, modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SegmentedChoice(
                options = listOf(FastingMode.OFF, FastingMode.TIMER, FastingMode.DAILY),
                selected = config.mode,
                label = { it.label },
                onSelect = { viewModel.setMode(config, it) },
            )

            if (config.mode != FastingMode.OFF) NotificationPermissionCard()

            when (config.mode) {
                FastingMode.OFF -> Text(
                    "Fasting is off. Pick Timer to start a fast whenever you like, or " +
                        "Schedule to keep the same eating window every day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                FastingMode.TIMER -> TimerMode(
                    status = status,
                    onStart = viewModel::startFast,
                    onEnd = { activeFast?.let(viewModel::endFast) },
                    onDiscard = viewModel::discardFast,
                )

                FastingMode.DAILY -> DailyMode(
                    status = status,
                    startMinute = config.dailyStartMinute,
                    endMinute = config.dailyEndMinute,
                    fastLength = FastingMath.dailyFastDuration(config),
                    onStartChange = { viewModel.setDailyWindow(config, it, config.dailyEndMinute) },
                    onEndChange = { viewModel.setDailyWindow(config, config.dailyStartMinute, it) },
                )
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
            }

            if (recentFasts.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "Recent fasts",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                recentFasts.forEach { FastHistoryRow(it) }
            }
        }
    }
}

@Composable
private fun TimerMode(
    status: FastingStatus,
    onStart: (Double) -> Unit,
    onEnd: () -> Unit,
    onDiscard: () -> Unit,
) {
    if (status is FastingStatus.Fasting) {
        RunningFast(status = status, onEnd = onEnd, onDiscard = onDiscard)
        return
    }

    var selectedHours by remember { mutableStateOf<Int?>(16) }
    var customHours by remember { mutableStateOf("") }

    val hours = customHours.toDoubleOrNull() ?: selectedHours?.toDouble()

    FastDialRow(
        hourOptions = FastingMath.PRESET_HOURS,
        selectedHours = if (customHours.isBlank()) selectedHours else null,
        onSelect = { selectedHours = it; customHours = "" },
    )

    DecimalField(
        label = "Or a custom length",
        value = customHours,
        onValueChange = { customHours = it; if (it.isNotBlank()) selectedHours = null },
        suffix = "hours",
    )

    hours?.let {
        val endsAt = Instant.now().plusMillis((it * 3_600_000L).toLong())
            .atZone(ZoneId.systemDefault()).toLocalDateTime()
        Text(
            "Ends ${endsAt.format(CLOCK_WITH_DAY)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Button(
        enabled = hours != null && hours > 0,
        onClick = { hours?.let(onStart) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Start fasting") }
}

@Composable
private fun RunningFast(
    status: FastingStatus.Fasting,
    onEnd: () -> Unit,
    onDiscard: () -> Unit,
) {
    RingGauge(
        progress = status.progress,
        color = if (status.isPastGoal) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.primary,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (status.isPastGoal) "Goal reached" else status.remaining.toCompactString(),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                if (status.isPastGoal) "${status.elapsed.toCompactString()} fasted" else "remaining",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Text(
        "Started ${status.startedAt.format(CLOCK_WITH_DAY)} · ends ${status.endsAt.format(CLOCK_WITH_DAY)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    Button(onClick = onEnd, modifier = Modifier.fillMaxWidth()) {
        Text(if (status.isPastGoal) "Finish fast" else "End fast early")
    }
    TextButton(onClick = onDiscard) { Text("Discard without recording") }
}

@Composable
private fun DailyMode(
    status: FastingStatus,
    startMinute: Int,
    endMinute: Int,
    fastLength: Duration,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    FastingStatusCard(status)

    TimeField("Stop eating at", startMinute, onStartChange)
    TimeField("Start eating at", endMinute, onEndChange)

    Text(
        "That's a ${fastLength.toCompactString()} fast every day.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Shared status block: the same words the home screen uses, so the two never disagree. */
@Composable
fun FastingStatusCard(status: FastingStatus, modifier: Modifier = Modifier) {
    val (headline, detail) = status.describe()
    if (headline == null) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (status is FastingStatus.Fasting) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                headline,
                style = MaterialTheme.typography.titleMedium,
                color = if (status is FastingStatus.Fasting) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status is FastingStatus.Fasting) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** @return headline (null when there is nothing to say) and an optional detail line. */
fun FastingStatus.describe(): Pair<String?, String?> = when (this) {
    FastingStatus.Disabled -> null to null
    FastingStatus.Idle -> "Not fasting" to "Start one whenever you're ready."
    is FastingStatus.Fasting ->
        if (isPastGoal) "Goal reached · ${elapsed.toCompactString()} fasted" to
            "Started ${startedAt.format(CLOCK)}."
        else "Fasting · ${remaining.toCompactString()} left" to
            "Started ${startedAt.format(CLOCK)}, ends ${endsAt.format(CLOCK)}."
    is FastingStatus.EatingWindow -> "Eating window" to
        "Closes in ${untilNextFast.toCompactString()}, at ${nextFastStartsAt.format(CLOCK)}."
}

@Composable
private fun FastHistoryRow(record: FastRecord) {
    val zone = ZoneId.systemDefault()
    val started = Instant.ofEpochMilli(record.startedAt).atZone(zone).toLocalDateTime()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            started.format(DateTimeFormatter.ofPattern("d MMM, HH:mm")),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            Duration.ofMinutes(record.actualMinutes).toCompactString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (record.reachedGoal) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

val FastingMode.label: String
    get() = when (this) {
        FastingMode.OFF -> "Off"
        FastingMode.TIMER -> "Timer"
        FastingMode.DAILY -> "Schedule"
    }

/**
 * Reminders are the only reason this app needs the notification permission, so it is asked
 * for here rather than at launch — and only once a fasting mode is actually switched on.
 */
@Composable
private fun NotificationPermissionCard() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(FastingNotifications.hasPermission(context)) }
    var denied by remember { mutableStateOf(false) }

    // Coming back from the system settings screen is a resume, not a recomposition, so the
    // state has to be re-read there or the card would linger after the user said yes.
    LifecycleResumeEffect(Unit) {
        granted = FastingNotifications.hasPermission(context)
        onPauseOrDispose { }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        granted = allowed
        denied = !allowed
    }

    if (granted) return

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Reminders are off. CalCalc can nudge you ${FastingScheduler.LEAD_MINUTES} minutes " +
                    "before a fast starts and before it ends.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (denied) {
                // A second request is ignored once the user has refused, so send them to
                // the only place that can still turn it on.
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }
                ) { Text("Open notification settings") }
            } else {
                TextButton(
                    onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                ) { Text("Turn reminders on") }
            }
        }
    }
}
