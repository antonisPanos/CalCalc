package com.example.calcalc.ai

import com.example.calcalc.data.model.FoodItem
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** A turn in the food-logging conversation, as sent to the model. */
data class TurnInput(
    val text: String?,
    /** Base64 JPEG, already downscaled. */
    val imageBase64: String? = null,
)

/** Failures the chat UI knows how to explain, instead of surfacing a raw exception. */
sealed class GeminiError(message: String) : Exception(message) {
    object MissingKey : GeminiError("No Gemini API key set. Add one in Profile.")
    object InvalidKey : GeminiError("That Gemini API key was rejected. Check it in Profile.")
    object RateLimited : GeminiError("Gemini is rate-limiting or out of quota. Try again shortly.")
    object Offline : GeminiError("No connection. The message wasn't sent.")
    class Blocked(reason: String) : GeminiError("Gemini refused to answer ($reason).")
    class Unexpected(detail: String) : GeminiError(detail)
}

class GeminiRepository(
    private val api: GeminiApi,
    /** Indirection rather than [ApiKeyStore] so the repository is testable without Android. */
    private val apiKeyProvider: suspend () -> String?,
    private val moshi: Moshi,
) {
    /**
     * @param history prior turns of this meal's conversation, oldest first
     * @return the model's reply plus the full replacement item list
     */
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun sendTurn(
        history: List<Pair<String, TurnInput>>,
        current: TurnInput,
    ): Result<MealParse> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) return@withContext Result.failure(GeminiError.MissingKey)

        val contents = buildList {
            history.forEach { (role, turn) -> add(turn.toContent(role)) }
            add(current.toContent(ROLE_USER))
        }

        val request = GeminiRequest(
            systemInstruction = Content(parts = listOf(Part(text = MealPrompt.SYSTEM_INSTRUCTION))),
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = 0.2,
                responseMimeType = "application/json",
                responseSchema = MealPrompt.RESPONSE_SCHEMA,
            ),
        )

        runCatching { api.generateContent(MealPrompt.MODEL, apiKey, request) }
            .mapCatching { response ->
                response.promptFeedback?.blockReason?.let { throw GeminiError.Blocked(it) }
                val json = response.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstNotNullOfOrNull { it.text }
                    ?: throw GeminiError.Unexpected("Gemini returned an empty response.")

                moshi.adapter<MealParse>().fromJson(json)
                    ?: throw GeminiError.Unexpected("Couldn't read Gemini's response.")
            }
            .recoverCatching { throw it.toGeminiError() }
    }

    private fun TurnInput.toContent(role: String): Content = Content(
        role = role,
        parts = buildList {
            imageBase64?.let { add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = it))) }
            if (!text.isNullOrBlank()) add(Part(text = text))
            // Gemini rejects a message with no parts at all.
            if (isEmpty()) add(Part(text = ""))
        },
    )

    private fun Throwable.toGeminiError(): Throwable = when {
        this is GeminiError -> this
        // Must precede the IOException branch: Moshi's JsonEncodingException extends
        // IOException, and a garbled model reply is not a connectivity problem.
        this is JsonDataException || this is JsonEncodingException ->
            GeminiError.Unexpected("Couldn't read Gemini's response.")
        this is HttpException -> when (code()) {
            400, 401, 403 -> GeminiError.InvalidKey
            429 -> GeminiError.RateLimited
            in 500..599 -> GeminiError.Unexpected("Gemini is having trouble (HTTP ${code()}).")
            else -> GeminiError.Unexpected("Gemini call failed (HTTP ${code()}).")
        }
        this is IOException -> GeminiError.Offline
        else -> GeminiError.Unexpected(message ?: "Something went wrong talking to Gemini.")
    }

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_MODEL = "model"
    }
}

fun ParsedItem.toFoodItem(): FoodItem = FoodItem(
    name = name,
    quantity = quantity,
    calories = calories,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    confidence = confidence,
)
