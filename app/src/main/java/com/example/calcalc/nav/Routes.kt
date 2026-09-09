package com.example.calcalc.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

/**
 * @param entryId non-null when editing an entry that is already saved
 * @param dateLocal `yyyy-MM-dd` the entry belongs to; null means today
 * @param openCamera true when the user chose "take a photo" from Home
 */
@Serializable
data class ChatKey(
    val entryId: String? = null,
    val dateLocal: String? = null,
    val openCamera: Boolean = false,
) : NavKey

@Serializable
data object JournalKey : NavKey

@Serializable
data object WeightKey : NavKey

@Serializable
data object ProfileKey : NavKey
