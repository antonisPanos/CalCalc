package com.example.calcalc.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.calcalc.ServiceLocator
import com.example.calcalc.data.UserDataRepository
import com.example.calcalc.data.model.UserProfile
import com.example.calcalc.domain.CalorieMath
import com.example.calcalc.domain.CalorieTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Profile and derived calorie target for the signed-in user, held once at the root so every
 * screen shares a single Firestore listener instead of opening its own.
 */
class SessionViewModel(
    private val repo: UserDataRepository,
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

    companion object {
        val Factory = viewModelFactory {
            initializer { SessionViewModel(ServiceLocator.userRepository()) }
        }
    }
}

sealed interface ProfileState {
    data object Loading : ProfileState
    data object Missing : ProfileState
    data class Loaded(val profile: UserProfile) : ProfileState
}
