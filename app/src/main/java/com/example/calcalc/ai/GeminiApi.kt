package com.example.calcalc.ai

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiApi {

    /**
     * The key travels in a header rather than the query string so it does not end up in
     * URL logs or crash reports.
     */
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body body: GeminiRequest,
    ): GeminiResponse
}
