package com.example.calcalc.ai

/**
 * The model is asked to return the *complete* item list on every turn, not a diff.
 * "Actually make that three eggs" or "drop the fries" then needs no merge logic on our
 * side — the ViewModel simply replaces the table with what came back.
 */
object MealPrompt {

    /**
     * Google retires older models for new API keys without warning — 2.5-flash now answers
     * 404 for keys created after its cutoff. If this starts 404-ing, the error surfaced in
     * chat carries Google's own replacement suggestion.
     */
    const val MODEL = "gemini-3.6-flash"

    val SYSTEM_INSTRUCTION = """
        You are a nutrition estimator inside a calorie-tracking app. The user describes or
        photographs what they ate; you estimate it.

        Rules:
        - Always return the COMPLETE current list of food items for this meal, reflecting every
          correction made so far in the conversation. Never return only the change.
        - If the user removes an item, omit it from the list. If they correct a quantity,
          return the corrected item.
        - Use metric units in `quantity` (g, ml, or natural counts like "2 slices").
        - `calories` is total kcal for the stated quantity, not per 100 g.
        - Estimate macros when you reasonably can; leave them null when you cannot.
        - `confidence` is 0.0-1.0: how sure you are of the calorie figure. Photographs of
          mixed dishes deserve lower confidence than a labelled packaged food.
        - `reply` is one or two short sentences to the user: what you logged, and a question
          only when a genuinely ambiguous detail would change the estimate a lot
          (e.g. fried vs grilled, portion size). Do not ask about trivia.
        - If the message contains no food at all, return an empty item list and say so in `reply`.
    """.trimIndent()

    /** OpenAPI-subset schema; forces valid JSON out of the model so parsing cannot drift. */
    val RESPONSE_SCHEMA: Map<String, Any> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "reply" to mapOf(
                "type" to "STRING",
                "description" to "Short message to the user about what was logged.",
            ),
            "items" to mapOf(
                "type" to "ARRAY",
                "description" to "The complete list of food items for this meal.",
                "items" to mapOf(
                    "type" to "OBJECT",
                    "properties" to mapOf(
                        "name" to mapOf("type" to "STRING"),
                        "quantity" to mapOf("type" to "STRING"),
                        "calories" to mapOf("type" to "INTEGER"),
                        "protein_g" to mapOf("type" to "NUMBER", "nullable" to true),
                        "carbs_g" to mapOf("type" to "NUMBER", "nullable" to true),
                        "fat_g" to mapOf("type" to "NUMBER", "nullable" to true),
                        "confidence" to mapOf("type" to "NUMBER", "nullable" to true),
                    ),
                    "required" to listOf("name", "quantity", "calories"),
                    "propertyOrdering" to listOf(
                        "name", "quantity", "calories", "protein_g", "carbs_g", "fat_g", "confidence"
                    ),
                ),
            ),
        ),
        "required" to listOf("reply", "items"),
        "propertyOrdering" to listOf("reply", "items"),
    )
}
