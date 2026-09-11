package com.example.calcalc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.calcalc.ServiceLocator
import com.example.calcalc.data.FirebaseConfig
import com.example.calcalc.data.model.UserProfile
import com.example.calcalc.nav.ChatKey
import com.example.calcalc.nav.FastingKey
import com.example.calcalc.nav.HomeKey
import com.example.calcalc.nav.JournalKey
import com.example.calcalc.nav.ProfileKey
import com.example.calcalc.nav.WeightKey
import com.example.calcalc.ui.chat.ChatScreen
import com.example.calcalc.ui.fasting.FastingScreen
import com.example.calcalc.ui.home.HomeScreen
import com.example.calcalc.ui.journal.JournalScreen
import com.example.calcalc.ui.onboarding.OnboardingScreen
import com.example.calcalc.ui.profile.ProfileScreen
import com.example.calcalc.ui.weight.WeightScreen
import kotlinx.coroutines.launch

@Composable
fun AppRoot() {
    val context = LocalContext.current
    if (!FirebaseConfig.isConfigured(context)) {
        FirebaseSetupScreen()
        return
    }

    val authRepository = ServiceLocator.authRepository
    val authFlow = remember { authRepository.authState() }
    val user by authFlow.collectAsStateWithLifecycle(initialValue = authRepository.currentUser)

    val currentUser = user
    if (currentUser == null) {
        SignInScreen()
        return
    }

    // Re-keying on uid throws away every cached ViewModel when the account changes, so no
    // screen can keep showing the previous user's data.
    key(currentUser.uid) { SignedInApp() }
}

@Composable
private fun SignedInApp() {
    val session: SessionViewModel = viewModel(factory = SessionViewModel.Factory)
    val profileState by session.profile.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val saveProfile: (UserProfile) -> Unit = { profile ->
        saving = true
        saveError = null
        scope.launch {
            runCatching { ServiceLocator.userRepository().saveProfile(profile) }
                .onFailure { saveError = it.message ?: "Couldn't save your profile." }
            saving = false
        }
    }

    when (val state = profileState) {
        ProfileState.Loading -> LoadingScreen()

        ProfileState.Missing -> OnboardingScreen(onSave = saveProfile, saving = saving, error = saveError)

        is ProfileState.Loaded ->
            if (!state.profile.isComplete) {
                OnboardingScreen(onSave = saveProfile, saving = saving, error = saveError)
            } else {
                MainScaffold(session)
            }
    }
}

private data class BottomDestination(val key: NavKey, val label: String, val icon: ImageVector)

@Composable
private fun MainScaffold(session: SessionViewModel) {
    val backStack = rememberNavBackStack(HomeKey)
    val target by session.target.collectAsStateWithLifecycle()
    val currentKey = backStack.lastOrNull()

    // Five destinations, with logging in the middle because it is the one used daily.
    // Home is not a tab: it is what Back and the screens' back arrows return to.
    val destinations = listOf(
        BottomDestination(ProfileKey, "Profile", Icons.Default.Person),
        BottomDestination(JournalKey, "Journal", Icons.AutoMirrored.Filled.ShowChart),
        BottomDestination(ChatKey(), "Log", Icons.Default.Restaurant),
        BottomDestination(FastingKey, "Fasting", Icons.Default.HourglassEmpty),
        BottomDestination(WeightKey, "Weight", Icons.Default.MonitorWeight),
    )

    /** Tab switching replaces the tab rather than stacking, so Back always means "go home". */
    fun switchTo(key: NavKey) {
        if (currentKey == key) return
        if (key == HomeKey) {
            backStack.clear()
            backStack.add(HomeKey)
        } else {
            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            backStack.add(key)
        }
    }

    Scaffold(
        bottomBar = {
            // The chat is deliberately chrome-free: the spec wants the menu out of the way
            // while logging a meal.
            if (currentKey !is ChatKey) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentKey == destination.key,
                            onClick = {
                                switchTo(if (currentKey == destination.key) HomeKey else destination.key)
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // Consume as well as apply: without this the chat's own Scaffold and the input
        // bar's navigationBarsPadding re-add the same system-bar insets, stacking up to
        // three times the intended gap at the bottom.
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                transitionSpec = NavTransitions.push(),
                popTransitionSpec = NavTransitions.pop(),
                predictivePopTransitionSpec = NavTransitions.predictivePop(),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<HomeKey> {
                        HomeScreen(
                            session = session,
                            onOpenEntry = { id -> backStack.add(ChatKey(entryId = id)) },
                            onOpenFasting = { switchTo(FastingKey) },
                        )
                    }
                    entry<ChatKey> { chatKey ->
                        ChatScreen(
                            args = chatKey,
                            onDone = { backStack.removeLastOrNull() },
                            onOpenProfile = {
                                backStack.removeLastOrNull()
                                switchTo(ProfileKey)
                            },
                        )
                    }
                    entry<JournalKey> {
                        JournalScreen(
                            target = target,
                            onEditEntry = { id -> backStack.add(ChatKey(entryId = id)) },
                            onAddForDate = { date -> backStack.add(ChatKey(dateLocal = date.toString())) },
                            onBack = { switchTo(HomeKey) },
                        )
                    }
                    entry<FastingKey> { FastingScreen(session = session, onBack = { switchTo(HomeKey) }) }
                    entry<WeightKey> { WeightScreen(session = session, onBack = { switchTo(HomeKey) }) }
                    entry<ProfileKey> { ProfileScreen(session = session, onBack = { switchTo(HomeKey) }) }
                },
            )
        }
    }
}
