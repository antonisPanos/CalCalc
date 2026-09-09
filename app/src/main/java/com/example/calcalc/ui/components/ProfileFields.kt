package com.example.calcalc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Decimal input that keeps the raw text, so a half-typed "7." doesn't get erased. */
@Composable
fun DecimalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label(option)) }
        }
    }
}

/** Vertical list of tappable cards — used where each option needs a line of explanation. */
@Composable
fun <T> CardChoice(
    options: List<T>,
    selected: T?,
    title: (T) -> String,
    detail: (T) -> String?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Card(
                onClick = { onSelect(option) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        title(option),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    detail(option)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Pick a date",
    yearRange: IntRange = 1920..LocalDate.now().year + 10,
) {
    var showPicker by remember { mutableStateOf(false) }

    Card(
        onClick = { showPicker = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                date?.format(DISPLAY_DATE) ?: placeholder,
                color = if (date != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showPicker) {
        val state = rememberDatePickerStateFor(date, yearRange)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDateChange(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberDatePickerStateFor(
    date: LocalDate?,
    yearRange: IntRange,
) = androidx.compose.material3.rememberDatePickerState(
    // The picker works in UTC millis, so convert at the UTC midnight of the chosen day to
    // avoid the date shifting by one in negative-offset timezones.
    initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    yearRange = yearRange,
)
