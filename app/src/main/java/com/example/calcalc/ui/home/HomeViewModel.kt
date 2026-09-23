package com.example.calcalc.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.calcalc.ServiceLocator
import com.example.calcalc.data.UserDataRepository
import com.example.calcalc.data.model.JournalEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(repo: UserDataRepository) : ViewModel() {

    /**
     * The day being shown. Driven from the screen's clock rather than read once, because the
     * ViewModel can outlive midnight while the app sits in memory.
     */
    private val today = MutableStateFlow(LocalDate.now())

    val todayEntries: StateFlow<List<JournalEntry>> = today
        .flatMapLatest { repo.entriesForDay(it).catch { emit(emptyList()) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setToday(date: LocalDate) {
        today.value = date
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(ServiceLocator.userRepository()) }
        }
    }
}
