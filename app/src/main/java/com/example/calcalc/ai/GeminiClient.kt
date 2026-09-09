package com.example.calcalc.ai

import com.example.calcalc.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object GeminiClient {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    /**
     * Reflection adapter is registered last as a fallback; generated adapters (from the
     * `@JsonClass` annotations) still win for the DTOs that have them.
     */
    val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    fun create(): GeminiApi {
        val logging = HttpLoggingInterceptor().apply {
            // Bodies carry base64 images and the API key header, so only ever log on debug.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            // Vision requests on a slow connection routinely need more than the 10s default.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApi::class.java)
    }
}
