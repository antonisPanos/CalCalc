package com.example.calcalc.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.calcalc.ServiceLocator
import com.example.calcalc.data.UserDataRepository
import com.example.calcalc.data.model.JournalEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class JournalRange(val days: Int, val label: String) {
    WEEK(7, "7d"),
    MONTH(30, "30d"),
    QUARTER(90, "90d"),
}

data class DaySummary(
    val date: LocalDate,
    val entries: List<JournalEntry>,
) {
    val totalCalories: Int get() = entries.sumOf { it.totalCalories }
}

class JournalViewModel(private val repo: UserDataRepository) : ViewModel() {

    private val _range = MutableStateFlow(JournalRange.WEEK)
    val range: StateFlow<JournalRange> = _range.asStateFlow()

    private val entries: StateFlow<List<JournalEntry>> = repo.recentEntries()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Days newest first, including days with no entries so gaps are visible in the chart. */
    val days: StateFlow<List<DaySummary>> = combine(entries, _range) { all, range ->
        val today = LocalDate.now()
        val byDate = all.groupBy { it.date }
        (0 until range.days).map { offset ->
            val date = today.minusDays(offset.toLong())
            DaySummary(date = date, entries = byDate[date].orEmpty().sortedBy { it.createdAt })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setRange(range: JournalRange) {
        _range.value = range
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch { runCatching { repo.deleteEntry(id) } }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { JournalViewModel(ServiceLocator.userRepository()) }
        }
    }
}
