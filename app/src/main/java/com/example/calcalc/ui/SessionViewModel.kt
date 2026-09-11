package com.example.calcalc.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.calcalc.ServiceLocator
import com.example.calcalc.data.UserDataRepository
import com.example.calcalc.data.model.ActiveFast
import com.example.calcalc.data.model.FastingConfig
import com.example.calcalc.data.model.UserProfile
import com.example.calcalc.domain.CalorieMath
import com.example.calcalc.domain.CalorieTarget
import com.example.calcalc.notify.FastingScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Profile and derived calorie target for the signed-in user, held once at the root so every
 * screen shares a single Firestore listener instead of opening its own.
 */
class SessionViewModel(
    private val repo: UserDataRepository,
    /**
     * Called whenever the fasting settings change, to re-arm the reminder alarms. A lambda
     * rather than a Context keeps this ViewModel free of Android dependencies.
     */
    private val onFastingChanged: (FastingConfig, ActiveFast?) -> Unit = { _, _ -> },
) : ViewModel() {

    /** null while loading, then either the profile or [UserProfile] absent. */
    val profile: StateFlow<ProfileState> = repo.profileFlow()
        .catch { emit(null) }
        .map { if (it == null) ProfileState.Missing else ProfileState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileState.Loading)

    /** Newest logged weight, falling back to the profile's starting weight. */
    val currentWeightKg: StateFlow<Double?> = combine(
        repo.weightsDescending(limit = 1).catch { emit(emptyList()) },
        profile,
    ) { weights, profileState ->
        weights.firstOrNull()?.weightKg
            ?: (profileState as? ProfileState.Loaded)?.profile?.weightKg?.takeIf { it > 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val target: StateFlow<CalorieTarget?> = combine(profile, currentWeightKg) { profileState, weight ->
        val loaded = (profileState as? ProfileState.Loaded)?.profile ?: return@combine null
        val effectiveWeight = weight ?: loaded.weightKg
        CalorieMath.dailyTarget(loaded, effectiveWeight)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Fasting config and the running fast live here rather than in the fasting screen: the
    // home screen shows the same status, and one listener is enough for both.
    val fastingConfig: StateFlow<FastingConfig> = repo.fastingConfigFlow()
        .catch { emit(FastingConfig()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FastingConfig())

    val activeFast: StateFlow<ActiveFast?> = repo.activeFastFlow()
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Alarms are absolute times held by the system, so they only need re-arming when the
        // schedule itself changes — plus once at startup, which covers a reboot.
        viewModelScope.launch {
            combine(fastingConfig, activeFast) { config, active -> config to active }
                .distinctUntilChanged()
                .collect { (config, active) -> onFastingChanged(config, active) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                SessionViewModel(
                    repo = ServiceLocator.userRepository(),
                    onFastingChanged = { config, active ->
                        FastingScheduler.sync(ServiceLocator.applicationContext, config, active)
                    },
                )
            }
        }
    }
}

sealed interface ProfileState {
    data object Loading : ProfileState
    data object Missing : ProfileState
    data class Loaded(val profile: UserProfile) : ProfileState
}
