package com.example.calcalc.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.calcalc.ServiceLocator
import com.example.calcalc.data.model.ActivityLevel
import com.example.calcalc.data.model.Goal
import com.example.calcalc.data.model.Sex
import com.example.calcalc.ui.ProfileState
import com.example.calcalc.ui.SessionViewModel
import com.example.calcalc.ui.components.CardChoice
import com.example.calcalc.ui.components.DateField
import com.example.calcalc.ui.components.DecimalField
import com.example.calcalc.ui.components.SegmentedChoice
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ProfileScreen(
    session: SessionViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileState by session.profile.collectAsStateWithLifecycle()
    val target by session.target.collectAsStateWithLifecycle()
    val profile = (profileState as? ProfileState.Loaded)?.profile

    var draft by remember(profile) { mutableStateOf(ProfileDraft.from(profile)) }
    var status by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall)

        target?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Daily target ${it.target} kcal", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "BMR ${it.bmr} · burn ${it.tdee} · adjustment ${it.appliedDelta} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SegmentedChoice(
            options = listOf(Sex.MALE, Sex.FEMALE),
            selected = draft.sex,
            label = { it.label },
            onSelect = { draft = draft.copy(sex = it) },
        )
        DateField(
            label = "Date of birth",
            date = draft.birthDate,
            onDateChange = { draft = draft.copy(birthDate = it) },
            yearRange = 1920..LocalDate.now().year,
        )
        DecimalField("Height", draft.heightCm, { draft = draft.copy(heightCm = it) }, "cm")
        DecimalField(
            label = "Starting weight",
            value = draft.weightKg,
            onValueChange = { draft = draft.copy(weightKg = it) },
            suffix = "kg",
        )
        Text(
            "Your current weight comes from the Weight tab; this is only the starting point.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Typical day", style = MaterialTheme.typography.titleMedium)
        CardChoice(
            options = ActivityLevel.entries,
            selected = draft.activityLevel,
            title = { it.label },
            detail = { it.detail },
            onSelect = { draft = draft.copy(activityLevel = it) },
        )

        Text("Goal", style = MaterialTheme.typography.titleMedium)
        SegmentedChoice(
            options = listOf(Goal.MAINTAIN, Goal.LOSE, Goal.GAIN),
            selected = draft.goal,
            label = { it.label },
            onSelect = { draft = draft.copy(goal = it) },
        )
        if (draft.goal != Goal.MAINTAIN) {
            DecimalField("Target weight", draft.goalWeightKg, { draft = draft.copy(goalWeightKg = it) }, "kg")
            DateField(
                label = "Target date",
                date = draft.goalDate,
                onDateChange = { draft = draft.copy(goalDate = it) },
                placeholder = "Optional",
                yearRange = LocalDate.now().year..LocalDate.now().year + 10,
            )
        }

        Button(
            enabled = draft.isValid,
            onClick = {
                scope.launch {
                    runCatching { ServiceLocator.userRepository().saveProfile(draft.toProfile(profile)) }
                        .onSuccess { status = "Saved." }
                        .onFailure { status = it.message ?: "Couldn't save." }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save profile") }

        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        ApiKeySection()

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        OutlinedButton(
            onClick = { scope.launch { ServiceLocator.authRepository.signOut(context) } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sign out") }
    }
}

@Composable
private fun ApiKeySection() {
    val scope = rememberCoroutineScope()
    val store = ServiceLocator.apiKeyStore
    var key by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    // Load once; afterwards the field is whatever the user is typing.
    LaunchedEffect(Unit) { key = store.currentKey().orEmpty() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Gemini API key", style = MaterialTheme.typography.titleMedium)
        Text(
            "Used to read your meals. Stored encrypted on this device only, so you'll need to " +
                "enter it again on a new phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it; status = null },
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(
                        if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (reveal) "Hide key" else "Show key",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                scope.launch {
                    store.setApiKey(key)
                    status = if (key.isBlank()) "Key cleared." else "Key saved."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save key") }
        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
