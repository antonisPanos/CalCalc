package com.example.calcalc

import com.example.calcalc.ai.Candidate
import com.example.calcalc.ai.Content
import com.example.calcalc.ai.GeminiApi
import com.example.calcalc.ai.GeminiError
import com.example.calcalc.ai.GeminiRepository
import com.example.calcalc.ai.GeminiRequest
import com.example.calcalc.ai.GeminiResponse
import com.example.calcalc.ai.Part
import com.example.calcalc.ai.PromptFeedback
import com.example.calcalc.ai.TurnInput
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class GeminiRepositoryTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private fun repo(
        apiKey: String? = "test-key",
        respond: suspend () -> GeminiResponse,
    ) = GeminiRepository(
        api = object : GeminiApi {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                body: GeminiRequest,
            ): GeminiResponse = respond()
        },
        apiKeyProvider = { apiKey },
        moshi = moshi,
    )

    private fun jsonResponse(json: String) = GeminiResponse(
        candidates = listOf(Candidate(content = Content(parts = listOf(Part(text = json)))))
    )

    @Test
    fun `parses a well formed response`() = runTest {
        val json = """
            {"reply":"Logged your breakfast.","items":[
              {"name":"Fried egg","quantity":"2 eggs","calories":180,"protein_g":12.5,"confidence":0.8},
              {"name":"Toast","quantity":"1 slice","calories":90}
            ]}
        """.trimIndent()

        val result = repo { jsonResponse(json) }.sendTurn(emptyList(), TurnInput("two eggs and toast"))

        val parse = result.getOrThrow()
        assertEquals("Logged your breakfast.", parse.reply)
        assertEquals(2, parse.items.size)
        assertEquals("Fried egg", parse.items[0].name)
        assertEquals(180, parse.items[0].calories)
        assertEquals(12.5, parse.items[0].proteinG!!, 0.001)
        // Absent macros stay null rather than defaulting to zero.
        assertEquals(null, parse.items[1].proteinG)
    }

    @Test
    fun `an empty item list is valid`() = runTest {
        val result = repo { jsonResponse("""{"reply":"I don't see any food there.","items":[]}""") }
            .sendTurn(emptyList(), TurnInput("hello"))

        assertTrue(result.getOrThrow().items.isEmpty())
    }

    @Test
    fun `a malformed body is an error, not a crash`() = runTest {
        val result = repo { jsonResponse("not json at all") }
            .sendTurn(emptyList(), TurnInput("eggs"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GeminiError.Unexpected)
    }

    @Test
    fun `a response with no candidates is an error`() = runTest {
        val result = repo { GeminiResponse(candidates = emptyList()) }
            .sendTurn(emptyList(), TurnInput("eggs"))

        assertTrue(result.exceptionOrNull() is GeminiError.Unexpected)
    }

    @Test
    fun `a missing key is reported before any network call`() = runTest {
        val result = repo(apiKey = null) { error("must not be called") }
            .sendTurn(emptyList(), TurnInput("eggs"))

        assertEquals(GeminiError.MissingKey, result.exceptionOrNull())
    }

    @Test
    fun `a rejected key maps to InvalidKey`() = runTest {
        val result = repo { throw httpError(400) }.sendTurn(emptyList(), TurnInput("eggs"))
        assertEquals(GeminiError.InvalidKey, result.exceptionOrNull())
    }

    @Test
    fun `a retired model surfaces Google's own explanation`() = runTest {
        val body = """{"error":{"code":404,"message":"This model models/gemini-2.5-flash is no longer available to new users.","status":"NOT_FOUND"}}"""
        val result = repo { throw httpError(404, body) }.sendTurn(emptyList(), TurnInput("eggs"))

        val error = result.exceptionOrNull()
        assertTrue(error is GeminiError.ModelUnavailable)
        assertTrue(error!!.message!!.contains("no longer available to new users"))
    }

    @Test
    fun `a 404 with no readable body still reports the model`() = runTest {
        val result = repo { throw httpError(404, "") }.sendTurn(emptyList(), TurnInput("eggs"))
        assertTrue(result.exceptionOrNull() is GeminiError.ModelUnavailable)
    }

    @Test
    fun `quota exhaustion maps to RateLimited`() = runTest {
        val result = repo { throw httpError(429) }.sendTurn(emptyList(), TurnInput("eggs"))
        assertEquals(GeminiError.RateLimited, result.exceptionOrNull())
    }

    @Test
    fun `no network maps to Offline`() = runTest {
        val result = repo { throw IOException("no route to host") }
            .sendTurn(emptyList(), TurnInput("eggs"))
        assertEquals(GeminiError.Offline, result.exceptionOrNull())
    }

    @Test
    fun `a blocked prompt is surfaced as Blocked`() = runTest {
        val result = repo { GeminiResponse(promptFeedback = PromptFeedback(blockReason = "SAFETY")) }
            .sendTurn(emptyList(), TurnInput("eggs"))

        assertTrue(result.exceptionOrNull() is GeminiError.Blocked)
    }

    private fun httpError(code: Int, body: String = "{}") = HttpException(
        Response.error<GeminiResponse>(code, body.toResponseBody("application/json".toMediaType()))
    )
}
