package com.example.calcalc.ui.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calcalc.data.model.Goal
import com.example.calcalc.data.model.toLocalDateOrNull
import com.example.calcalc.ui.ProfileState
import com.example.calcalc.ui.SessionViewModel
import com.example.calcalc.ui.components.DayValue
import com.example.calcalc.ui.components.DecimalField
import com.example.calcalc.ui.components.WeightLineChart
import com.example.calcalc.ui.profile.trimmed
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DISPLAY_DATE = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun WeightScreen(
    session: SessionViewModel,
    modifier: Modifier = Modifier,
    viewModel: WeightViewModel = viewModel(factory = WeightViewModel.Factory),
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val profileState by session.profile.collectAsStateWithLifecycle()
    val target by session.target.collectAsStateWithLifecycle()
    val profile = (profileState as? ProfileState.Loaded)?.profile

    var input by remember { mutableStateOf("") }

    // Oldest first for the chart; the list below stays newest first.
    val points = remember(logs) {
        logs.mapNotNull { log ->
            log.dateLocal.toLocalDateOrNull()?.let { DayValue(it, log.weightKg.toFloat()) }
        }.sortedBy { it.date }
    }

    val projection = remember(points, profile, target) {
        val goalWeight = profile?.goalWeightKg
        val goalDate = target?.realisticGoalDate
        if (profile?.goal == Goal.MAINTAIN || goalWeight == null || goalDate == null || points.isEmpty()) {
            emptyList()
        } else {
            listOf(DayValue(goalDate, goalWeight.toFloat()))
        }
    }

    Column(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Weight", style = MaterialTheme.typography.headlineSmall)

        val latest = logs.firstOrNull()?.weightKg
        SummaryCard(latest = latest, goal = profile?.goalWeightKg, goalDate = target?.realisticGoalDate)

        WeightLineChart(points = points, projection = projection)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DecimalField(
                label = "Today's weight",
                value = input,
                onValueChange = { input = it },
                suffix = "kg",
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = input.toDoubleOrNull()?.let { it in 25.0..400.0 } == true,
                onClick = {
                    input.toDoubleOrNull()?.let { viewModel.log(it) }
                    input = ""
                },
            ) { Text("Log") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(logs, key = { it.dateLocal }) { log ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            log.dateLocal.toLocalDateOrNull()?.format(DISPLAY_DATE) ?: log.dateLocal,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${log.weightKg.trimmed()} kg",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            IconButton(onClick = { viewModel.delete(log.dateLocal) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete ${log.dateLocal}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(latest: Double?, goal: Double?, goalDate: LocalDate?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                latest?.let { "${it.trimmed()} kg" } ?: "No weight logged yet",
                style = MaterialTheme.typography.titleLarge,
            )
            val detail = when {
                latest == null || goal == null -> null
                else -> buildString {
                    val delta = goal - latest
                    append(
                        when {
                            kotlin.math.abs(delta) < 0.05 -> "At your goal weight"
                            delta < 0 -> "${(-delta).trimmed()} kg to lose"
                            else -> "${delta.trimmed()} kg to gain"
                        }
                    )
                    goalDate?.let { append(" · on track for ${it.format(DISPLAY_DATE)}") }
                }
            }
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
