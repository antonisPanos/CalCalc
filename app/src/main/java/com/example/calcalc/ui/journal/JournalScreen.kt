package com.example.calcalc.ui.journal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calcalc.data.model.JournalEntry
import com.example.calcalc.domain.CalorieTarget
import com.example.calcalc.ui.components.CalorieBar
import com.example.calcalc.ui.components.CaloriesBarChart
import com.example.calcalc.ui.components.DayValue
import com.example.calcalc.ui.components.ScreenScaffold
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_LABEL = DateTimeFormatter.ofPattern("EEE d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    target: CalorieTarget?,
    onEditEntry: (String) -> Unit,
    onAddForDate: (LocalDate) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JournalViewModel = viewModel(factory = JournalViewModel.Factory),
) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var expandedDate by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // The chart reads left-to-right in time; the list below reads newest first.
    val chartDays = remember(days) {
        days.reversed().map { DayValue(it.date, it.totalCalories.toFloat()) }
    }

    ScreenScaffold(title = "Journal", onBack = onBack, modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    JournalRange.entries.forEach { option ->
                        FilterChip(
                            selected = option == range,
                            onClick = { viewModel.setRange(option) },
                            label = { Text(option.label) },
                        )
                    }
                }

                CaloriesBarChart(days = chartDays, target = target?.target ?: 0)

                LazyColumn(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(days, key = { it.date.toString() }) { day ->
                        DayCard(
                            day = day,
                            target = target?.target ?: 0,
                            expanded = expandedDate == day.date,
                            onToggle = { expandedDate = if (expandedDate == day.date) null else day.date },
                            onEditEntry = onEditEntry,
                            onDeleteEntry = viewModel::deleteEntry,
                            onRepeatEntry = viewModel::repeatToday,
                            onAdd = { onAddForDate(day.date) },
                        )
                    }
                }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun DayCard(
    day: DaySummary,
    target: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEditEntry: (String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onRepeatEntry: (JournalEntry) -> Unit,
    onAdd: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(day.date.format(DAY_LABEL), style = MaterialTheme.typography.titleSmall)
                val delta = if (target > 0) day.totalCalories - target else 0
                Text(
                    buildString {
                        append("${day.totalCalories} kcal")
                        if (target > 0 && day.totalCalories > 0) {
                            append(if (delta > 0) "  +$delta" else "  $delta")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        day.totalCalories == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                        delta > 0 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }
            CalorieBar(
                consumed = day.totalCalories,
                target = target,
                modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 8.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                day.entries.forEach { entry ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Same meals come round again; this copies one onto today without
                        // going through the chat.
                        IconButton(onClick = { onRepeatEntry(entry) }) {
                            Icon(
                                Icons.Default.Replay,
                                contentDescription = "Log this meal again today",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { onEditEntry(entry.id) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                entry.items.joinToString(", ") { it.name }.ifBlank { "Empty entry" },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${entry.totalCalories} kcal · tap to edit in chat",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDeleteEntry(entry.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete entry",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                TextButton(onClick = onAdd, modifier = Modifier.padding(bottom = 6.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Add to this day")
                }
            }
        }
    }
}
