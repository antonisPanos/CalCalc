package com.example.calcalc.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calcalc.data.model.JournalEntry
import com.example.calcalc.domain.CalorieTarget
import com.example.calcalc.ui.components.CalorieRing
import java.time.format.DateTimeFormatter

private val DISPLAY_DATE = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun HomeScreen(
    target: CalorieTarget?,
    onWriteItDown: () -> Unit,
    onTakePhoto: () -> Unit,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val entries by viewModel.todayEntries.collectAsStateWithLifecycle()
    val consumed = entries.sumOf { it.totalCalories }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            java.time.LocalDate.now().format(DISPLAY_DATE),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        CalorieRing(consumed = consumed, target = target?.target ?: 0)

        target?.let { TargetNote(it) }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onWriteItDown, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
                Text("  Write it down")
            }
            OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Text("  Photo")
            }
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
    }
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
