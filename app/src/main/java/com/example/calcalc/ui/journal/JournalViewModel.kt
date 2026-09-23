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

    /** One-shot confirmation or failure text, shown as a snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val entries: StateFlow<List<JournalEntry>> = repo.recentEntries()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Kept current by the screen, since this ViewModel can outlive midnight. */
    private val today = MutableStateFlow(LocalDate.now())

    /** Days newest first, including days with no entries so gaps are visible in the chart. */
    val days: StateFlow<List<DaySummary>> = combine(entries, _range, today) { all, range, today ->
        val byDate = all.groupBy { it.date }
        (0 until range.days).map { offset ->
            val date = today.minusDays(offset.toLong())
            DaySummary(date = date, entries = byDate[date].orEmpty().sortedBy { it.createdAt })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setToday(date: LocalDate) {
        today.value = date
    }

    fun setRange(range: JournalRange) {
        _range.value = range
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch { runCatching { repo.deleteEntry(id) } }
    }

    /**
     * Copies a past meal onto today as a brand-new entry. Breakfast tends to repeat, and
     * re-describing it to the model would cost a round-trip to get the same numbers back.
     */
    fun repeatToday(entry: JournalEntry) {
        if (entry.items.isEmpty()) return
        viewModelScope.launch {
            runCatching { repo.saveEntry(null, LocalDate.now(), entry.items, entry.source) }
                .onSuccess { _message.value = "Added ${entry.totalCalories} kcal to today." }
                .onFailure { _message.value = it.message ?: "Couldn't copy that meal." }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { JournalViewModel(ServiceLocator.userRepository()) }
        }
    }
}
