package com.example.calcalc.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.calcalc.ServiceLocator
import com.example.calcalc.ai.GeminiRepository
import com.example.calcalc.ai.MealParse
import com.example.calcalc.ai.ParsedItem
import com.example.calcalc.ai.TurnInput
import com.example.calcalc.ai.toFoodItem
import com.example.calcalc.data.UserDataRepository
import com.example.calcalc.data.model.EntrySource
import com.example.calcalc.data.model.FoodItem
import com.example.calcalc.nav.ChatKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class ChatRole { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
    val role: ChatRole,
    val text: String,
    /** Base64 JPEG shown as a thumbnail, for messages the user sent with a photo. */
    val imageBase64: String? = null,
)

data class ChatUiState(
    val date: LocalDate = LocalDate.now(),
    val entryId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val items: List<FoodItem> = emptyList(),
    val sending: Boolean = false,
    val saving: Boolean = false,
    val loading: Boolean = false,
    /** Set once the entry is committed, so the screen can navigate away. */
    val finished: Boolean = false,
    /** True when the failure was "no API key", which the UI turns into a link to Profile. */
    val needsApiKey: Boolean = false,
) {
    val canFinish: Boolean get() = items.isNotEmpty() && !sending && !saving
}

/**
 * Drives one meal's conversation.
 *
 * The model is asked to return the whole item list every turn, so corrections ("make that
 * three eggs", "drop the fries") need no merge logic here — [ChatUiState.items] is simply
 * replaced with whatever came back.
 */
class ChatViewModel(
    private val args: ChatKey,
    private val repo: UserDataRepository,
    private val gemini: GeminiRepository,
    private val moshi: Moshi,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChatUiState(
            date = args.dateLocal?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(),
            entryId = args.entryId,
            loading = !args.entryId.isNullOrEmpty(),
        )
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** What we replay to Gemini each turn: (role, turn). */
    private val history = mutableListOf<Pair<String, TurnInput>>()

    private var source = EntrySource.TEXT

    init {
        val entryId = args.entryId
        if (!entryId.isNullOrEmpty()) loadExistingEntry(entryId)
    }

    private fun loadExistingEntry(entryId: String) {
        viewModelScope.launch {
            val entry = runCatching { repo.entry(entryId) }.getOrNull()
            if (entry == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        messages = it.messages + ChatMessage(ChatRole.SYSTEM, "Couldn't load that entry."),
                    )
                }
                return@launch
            }
            source = entry.source
            // Seed the model's context with the items already saved, so a follow-up like
            // "remove the toast" is understood without re-describing the meal.
            history += GeminiRepository.ROLE_MODEL to TurnInput(text = encodeItems(entry.items))
            _state.update {
                it.copy(
                    loading = false,
                    entryId = entryId,
                    date = entry.date ?: it.date,
                    items = entry.items,
                    messages = listOf(
                        ChatMessage(ChatRole.SYSTEM, "Editing an entry from ${entry.dateLocal}.")
                    ),
                )
            }
        }
    }

    fun send(text: String?, imageBase64: String? = null) {
        if (text.isNullOrBlank() && imageBase64 == null) return
        if (imageBase64 != null) source = EntrySource.PHOTO

        val turn = TurnInput(text = text?.trim(), imageBase64 = imageBase64)
        _state.update {
            it.copy(
                sending = true,
                needsApiKey = false,
                messages = it.messages + ChatMessage(
                    role = ChatRole.USER,
                    text = text?.trim().orEmpty(),
                    imageBase64 = imageBase64,
                ),
            )
        }

        viewModelScope.launch {
            val result = gemini.sendTurn(history.toList(), turn)
            result.fold(
                onSuccess = { parse ->
                    history += GeminiRepository.ROLE_USER to turn
                    history += GeminiRepository.ROLE_MODEL to TurnInput(text = encode(parse))
                    _state.update {
                        it.copy(
                            sending = false,
                            items = parse.items.map(ParsedItem::toFoodItem),
                            messages = it.messages + ChatMessage(ChatRole.ASSISTANT, parse.reply),
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            sending = false,
                            needsApiKey = error is com.example.calcalc.ai.GeminiError.MissingKey,
                            messages = it.messages + ChatMessage(
                                ChatRole.SYSTEM,
                                error.message ?: "Something went wrong.",
                            ),
                        )
                    }
                },
            )
        }
    }

    /** Removes a row directly, without spending a model round-trip on an obvious mistake. */
    fun removeItem(index: Int) {
        _state.update { current ->
            val updated = current.items.toMutableList().apply { if (index in indices) removeAt(index) }
            // Keep the model's view of the table in sync with the user's edit.
            history += GeminiRepository.ROLE_MODEL to TurnInput(text = encodeItems(updated))
            current.copy(items = updated)
        }
    }

    fun finish() {
        val current = _state.value
        if (!current.canFinish) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { repo.saveEntry(current.entryId, current.date, current.items, source) }
                .onSuccess { _state.update { it.copy(saving = false, finished = true) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            saving = false,
                            messages = it.messages + ChatMessage(
                                ChatRole.SYSTEM,
                                error.message ?: "Couldn't save this entry.",
                            ),
                        )
                    }
                }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun encode(parse: MealParse): String = moshi.adapter<MealParse>().toJson(parse)

    private fun encodeItems(items: List<FoodItem>): String = encode(
        MealParse(
            reply = "",
            items = items.map {
                ParsedItem(
                    name = it.name,
                    quantity = it.quantity,
                    calories = it.calories,
                    proteinG = it.proteinG,
                    carbsG = it.carbsG,
                    fatG = it.fatG,
                    confidence = it.confidence,
                )
            },
        )
    )

    companion object {
        val ARGS_KEY = CreationExtras.Key<ChatKey>()

        fun factory(args: ChatKey) = viewModelFactory {
            initializer {
                ChatViewModel(
                    args = args,
                    repo = ServiceLocator.userRepository(),
                    gemini = ServiceLocator.geminiRepository,
                    moshi = com.example.calcalc.ai.GeminiClient.moshi,
                )
            }
        }
    }
}
