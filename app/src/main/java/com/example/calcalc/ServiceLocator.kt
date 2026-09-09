package com.example.calcalc

import android.content.Context
import com.example.calcalc.ai.GeminiClient
import com.example.calcalc.ai.GeminiRepository
import com.example.calcalc.auth.AuthRepository
import com.example.calcalc.data.ApiKeyStore
import com.example.calcalc.data.UserDataRepository

/**
 * Hand-rolled dependency container. A single-user app with a handful of screens does not
 * earn a DI framework.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val authRepository: AuthRepository by lazy { AuthRepository(appContext) }

    val apiKeyStore: ApiKeyStore by lazy { ApiKeyStore(appContext) }

    val geminiRepository: GeminiRepository by lazy {
        GeminiRepository(GeminiClient.create(), { apiKeyStore.currentKey() }, GeminiClient.moshi)
    }

    @Volatile private var cachedRepo: Pair<String, UserDataRepository>? = null

    /**
     * Only valid inside the signed-in part of the UI tree; the sign-in gate guarantees a uid
     * before any screen that needs data is composed.
     */
    fun userRepository(): UserDataRepository {
        val uid = authRepository.currentUid
            ?: error("No signed-in user; a data screen was shown outside the sign-in gate.")
        cachedRepo?.let { (cachedUid, repo) -> if (cachedUid == uid) return repo }
        return UserDataRepository(uid).also { cachedRepo = uid to it }
    }
}
