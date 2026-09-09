package com.example.calcalc.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.calcalc.ServiceLocator
import com.example.calcalc.data.UserDataRepository
import com.example.calcalc.data.model.JournalEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class HomeViewModel(repo: UserDataRepository) : ViewModel() {

    val todayEntries: StateFlow<List<JournalEntry>> =
        repo.entriesForDay(LocalDate.now())
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(ServiceLocator.userRepository()) }
        }
    }
}
