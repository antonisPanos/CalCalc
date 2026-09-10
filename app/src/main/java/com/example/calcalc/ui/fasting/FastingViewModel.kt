package com.example.calcalc.ui.fasting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.calcalc.ServiceLocator
import com.example.calcalc.data.UserDataRepository
import com.example.calcalc.data.model.ActiveFast
import com.example.calcalc.data.model.FastRecord
import com.example.calcalc.data.model.FastingConfig
import com.example.calcalc.data.model.FastingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FastingViewModel(private val repo: UserDataRepository) : ViewModel() {

    val recentFasts: StateFlow<List<FastRecord>> = repo.recentFasts()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setMode(config: FastingConfig, mode: FastingMode) {
        save(config.copy(mode = mode))
    }

    fun setDailyWindow(config: FastingConfig, startMinute: Int, endMinute: Int) {
        save(config.copy(dailyStartMinute = startMinute, dailyEndMinute = endMinute))
    }

    private fun save(config: FastingConfig) {
        viewModelScope.launch {
            runCatching { repo.saveFastingConfig(config) }
                .onFailure { _error.value = it.message ?: "Couldn't save fasting settings." }
        }
    }

    fun startFast(hours: Double) {
        if (hours <= 0) return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            runCatching { repo.startFast(now, now + (hours * 3_600_000L).toLong()) }
                .onFailure { _error.value = it.message ?: "Couldn't start the fast." }
        }
    }

    /** Files the fast in history. Used both for finishing early and for finishing on time. */
    fun endFast(active: ActiveFast) {
        viewModelScope.launch {
            runCatching { repo.endFast(active) }
                .onFailure { _error.value = it.message ?: "Couldn't end the fast." }
        }
    }

    /** Drops the fast without recording it — for a mis-tap, not a broken fast. */
    fun discardFast() {
        viewModelScope.launch {
            runCatching { repo.cancelFast() }
                .onFailure { _error.value = it.message ?: "Couldn't discard the fast." }
        }
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { FastingViewModel(ServiceLocator.userRepository()) }
        }
    }
}
