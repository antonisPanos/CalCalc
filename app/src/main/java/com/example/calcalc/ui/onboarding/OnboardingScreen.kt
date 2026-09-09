package com.example.calcalc.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.calcalc.data.model.ActivityLevel
import com.example.calcalc.data.model.Goal
import com.example.calcalc.data.model.Sex
import com.example.calcalc.data.model.UserProfile
import com.example.calcalc.ui.components.CardChoice
import com.example.calcalc.ui.components.DateField
import com.example.calcalc.ui.components.DecimalField
import com.example.calcalc.ui.components.SegmentedChoice
import com.example.calcalc.ui.profile.ProfileDraft
import com.example.calcalc.ui.profile.label
import java.time.LocalDate

/**
 * First-run wizard. Three short steps beat one long form: each question gets room for the
 * explanation that makes it answerable.
 */
@Composable
fun OnboardingScreen(
    onSave: (UserProfile) -> Unit,
    saving: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf(ProfileDraft()) }
    var step by remember { mutableIntStateOf(0) }
    val lastStep = 2

    val canAdvance = when (step) {
        0 -> draft.basicsValid
        1 -> true
        else -> draft.goalValid
    }

    Column(modifier.fillMaxSize().imePadding().padding(24.dp)) {
        LinearProgressIndicator(
            progress = { (step + 1) / (lastStep + 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.padding(8.dp))

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                0 -> BasicsStep(draft) { draft = it }
                1 -> ActivityStep(draft) { draft = it }
                else -> GoalStep(draft) { draft = it }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { step-- }, enabled = step > 0 && !saving) { Text("Back") }
            Button(
                enabled = canAdvance && !saving,
                onClick = {
                    if (step < lastStep) step++ else onSave(draft.toProfile(null))
                },
            ) { Text(if (step < lastStep) "Next" else if (saving) "Saving…" else "Finish") }
        }
    }
}

@Composable
private fun BasicsStep(draft: ProfileDraft, onChange: (ProfileDraft) -> Unit) {
    Text("About you", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Used to work out how many calories you burn just existing.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SegmentedChoice(
        options = listOf(Sex.MALE, Sex.FEMALE),
        selected = draft.sex,
        label = { it.label },
        onSelect = { onChange(draft.copy(sex = it)) },
    )
    DateField(
        label = "Date of birth",
        date = draft.birthDate,
        onDateChange = { onChange(draft.copy(birthDate = it)) },
        yearRange = 1920..LocalDate.now().year,
    )
    DecimalField(
        label = "Height",
        value = draft.heightCm,
        onValueChange = { onChange(draft.copy(heightCm = it)) },
        suffix = "cm",
    )
    DecimalField(
        label = "Weight",
        value = draft.weightKg,
        onValueChange = { onChange(draft.copy(weightKg = it)) },
        suffix = "kg",
    )
}

@Composable
private fun ActivityStep(draft: ProfileDraft, onChange: (ProfileDraft) -> Unit) {
    Text("Your typical day", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Pick what your job and week actually look like, not what you wish they looked like.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    CardChoice(
        options = ActivityLevel.entries,
        selected = draft.activityLevel,
        title = { it.label },
        detail = { it.detail },
        onSelect = { onChange(draft.copy(activityLevel = it)) },
    )
}

@Composable
private fun GoalStep(draft: ProfileDraft, onChange: (ProfileDraft) -> Unit) {
    Text("Your goal", style = MaterialTheme.typography.headlineSmall)
    SegmentedChoice(
        options = listOf(Goal.MAINTAIN, Goal.LOSE, Goal.GAIN),
        selected = draft.goal,
        label = { it.label },
        onSelect = { onChange(draft.copy(goal = it)) },
    )
    if (draft.goal != Goal.MAINTAIN) {
        DecimalField(
            label = "Target weight",
            value = draft.goalWeightKg,
            onValueChange = { onChange(draft.copy(goalWeightKg = it)) },
            suffix = "kg",
        )
        DateField(
            label = "Target date",
            date = draft.goalDate,
            onDateChange = { onChange(draft.copy(goalDate = it)) },
            placeholder = "Optional",
            yearRange = LocalDate.now().year..LocalDate.now().year + 10,
        )
        Text(
            "Leave the date empty and we'll aim for a steady 0.5 kg per week.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
