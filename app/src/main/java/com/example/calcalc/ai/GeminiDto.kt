package com.example.calcalc.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// --- Request ---

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val systemInstruction: Content? = null,
    val contents: List<Content>,
    val generationConfig: GenerationConfig,
)

@JsonClass(generateAdapter = true)
data class Content(
    val role: String? = null,
    val parts: List<Part>,
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null,
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    /** Base64, no wrapping. */
    val data: String,
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Double? = null,
    val responseMimeType: String? = null,
    /** OpenAPI-subset schema; built in [MealPrompt.RESPONSE_SCHEMA]. */
    val responseSchema: Map<String, Any>? = null,
)

// --- Response ---

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>? = null,
    val promptFeedback: PromptFeedback? = null,
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
)

@JsonClass(generateAdapter = true)
data class PromptFeedback(
    val blockReason: String? = null,
)

/** The structured payload the model is forced to return, via `responseSchema`. */
@JsonClass(generateAdapter = true)
data class MealParse(
    val reply: String = "",
    val items: List<ParsedItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ParsedItem(
    val name: String = "",
    val quantity: String = "",
    val calories: Int = 0,
    @Json(name = "protein_g") val proteinG: Double? = null,
    @Json(name = "carbs_g") val carbsG: Double? = null,
    @Json(name = "fat_g") val fatG: Double? = null,
    val confidence: Double? = null,
)

/** Google's standard error envelope, used to surface a useful message instead of a code. */
@JsonClass(generateAdapter = true)
data class ApiErrorEnvelope(val error: ApiError? = null)

@JsonClass(generateAdapter = true)
data class ApiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)
