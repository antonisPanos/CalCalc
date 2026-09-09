package com.example.calcalc

import com.example.calcalc.ai.MealParse
import com.example.calcalc.ai.ParsedItem
import com.example.calcalc.ai.toFoodItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chat replays the previous item list back to the model as JSON so a follow-up
 * correction has context. That round-trip must not lose fields.
 */
class ChatTurnEncodingTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `items survive an encode-decode round trip`() {
        val original = MealParse(
            reply = "",
            items = listOf(
                ParsedItem("Fried egg", "2 eggs", 180, proteinG = 12.5, fatG = 14.0, confidence = 0.8),
                ParsedItem("Toast", "1 slice", 90),
            ),
        )

        val adapter = moshi.adapter<MealParse>()
        val decoded = adapter.fromJson(adapter.toJson(original))!!

        assertEquals(original, decoded)
        assertTrue(adapter.toJson(original).contains("\"protein_g\""))
    }

    @Test
    fun `parsed items map onto the stored food item`() {
        val food = ParsedItem("Toast", "1 slice", 90, carbsG = 17.0).toFoodItem()
        assertEquals("Toast", food.name)
        assertEquals(90, food.calories)
        assertEquals(17.0, food.carbsG!!, 0.001)
        assertEquals(null, food.proteinG)
    }
}
