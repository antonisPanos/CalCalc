package com.example.calcalc.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.calcalc.ServiceLocator
import com.example.calcalc.data.UserDataRepository
import com.example.calcalc.data.model.WeightLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class WeightViewModel(private val repo: UserDataRepository) : ViewModel() {

    val logs: StateFlow<List<WeightLog>> = repo.weightsDescending()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun log(weightKg: Double, date: LocalDate = LocalDate.now()) {
        viewModelScope.launch { runCatching { repo.logWeight(date, weightKg) } }
    }

    fun delete(dateLocal: String) {
        val date = runCatching { LocalDate.parse(dateLocal) }.getOrNull() ?: return
        viewModelScope.launch { runCatching { repo.deleteWeight(date) } }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { WeightViewModel(ServiceLocator.userRepository()) }
        }
    }
}
