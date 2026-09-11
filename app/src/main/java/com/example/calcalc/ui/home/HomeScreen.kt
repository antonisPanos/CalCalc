package com.example.calcalc.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calcalc.data.model.JournalEntry
import com.example.calcalc.domain.CalorieTarget
import com.example.calcalc.domain.DayInsight
import com.example.calcalc.domain.FastingMath
import com.example.calcalc.domain.FastingStatus
import com.example.calcalc.domain.InsightTone
import com.example.calcalc.domain.NutritionInsight
import com.example.calcalc.ui.SessionViewModel
import com.example.calcalc.ui.components.CalorieRing
import com.example.calcalc.ui.components.rememberTickingNow
import com.example.calcalc.ui.fasting.FastingStatusCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DISPLAY_DATE = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * The dashboard. Logging is reached from the bottom bar rather than from here, so this
 * screen stays a read-only picture of the day: calories against target, fasting status,
 * and what has been eaten so far.
 */
@Composable
fun HomeScreen(
    session: SessionViewModel,
    onOpenEntry: (String) -> Unit,
    onOpenFasting: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val entries by viewModel.todayEntries.collectAsStateWithLifecycle()
    val target by session.target.collectAsStateWithLifecycle()
    val weightKg by session.currentWeightKg.collectAsStateWithLifecycle()
    val fastingConfig by session.fastingConfig.collectAsStateWithLifecycle()
    val activeFast by session.activeFast.collectAsStateWithLifecycle()

    // A minute is enough here; the fasting screen itself ticks every second.
    val now by rememberTickingNow(periodMillis = 60_000L)
    val fastingStatus = FastingMath.status(fastingConfig, activeFast, now)

    val consumed = entries.sumOf { it.totalCalories }

    // Recomputed on the hour rather than the minute: the wording only ever changes when the
    // day moves into a new phase.
    val insight = remember(entries, target, weightKg, now.hour) {
        NutritionInsight.evaluate(
            items = entries.flatMap { it.items },
            targetKcal = target?.target ?: 0,
            weightKg = weightKg,
            time = now.toLocalTime(),
        )
    }

    Column(
        modifier
            .fillMaxSize()
            // Opaque so the crossfade to another route does not show both screens at once.
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            LocalDate.now().format(DISPLAY_DATE),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        CalorieRing(consumed = consumed, target = target?.target ?: 0)

        target?.let { TargetNote(it) }

        if (fastingStatus != FastingStatus.Disabled) {
            FastingStatusCard(
                status = fastingStatus,
                modifier = Modifier.clickable(onClick = onOpenFasting),
            )
        }

        if (entries.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Today",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                entries.forEach { entry -> EntryRow(entry) { onOpenEntry(entry.id) } }
            }
        }

        insight?.let { InsightCard(it) }
    }
}

/**
 * The day's nutrition read. Every line is phrased against how far through the day it is —
 * a breakfast-only morning should not be told it is missing dinner.
 */
@Composable
private fun InsightCard(insight: DayInsight) {
    if (insight.lines.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "How today's going",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            insight.lines.forEach { line ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "•  ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = line.tone.color(),
                    )
                    Text(
                        line.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightTone.color() = when (this) {
    InsightTone.GOOD -> MaterialTheme.colorScheme.primary
    InsightTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    InsightTone.WARNING -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun TargetNote(target: CalorieTarget) {
    val text = buildString {
        append("Target ${target.target} kcal — burn ~${target.tdee}")
        when {
            target.appliedDelta < 0 -> append(", deficit ${-target.appliedDelta}")
            target.appliedDelta > 0 -> append(", surplus ${target.appliedDelta}")
        }
        append(".")
        if (target.isClamped) {
            append(
                "\nYour goal date needs ${target.requestedDelta} kcal/day, which isn't safe. " +
                    "Capped to a healthy rate"
            )
            target.realisticGoalDate?.let { append(" — on track for ${it.format(DISPLAY_DATE)}") }
            append(".")
        }
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (target.isClamped) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (target.isClamped) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun EntryRow(entry: JournalEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entry.items.joinToString(", ") { it.name }.ifBlank { "Empty entry" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${entry.totalCalories} kcal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
