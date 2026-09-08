package com.registry.coach.ai

import com.google.mlkit.genai.prompt.Generation
import com.registry.coach.filter.ContextGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device Intent Engine powered by Google Gemini Nano (ML Kit GenAI / AICore).
 * Executes 100% locally on the device chip for zero-latency, private intent classification.
 */
class GeminiNanoIntentEngine(private val contextGuard: ContextGuard) {

    data class IntentResult(
        val intentName: String,
        val confidence: Float,
        val suggestedAction: String?,
        val executionPayload: String?,
        val isOfflineOnly: Boolean = true
    )

    /**
     * Evaluates on-screen text to detect automation intent.
     * Guaranteed never to emit or upload text over the network.
     */
    suspend fun evaluateOnScreenIntent(
        foregroundPackage: String,
        screenText: String
    ): IntentResult? = withContext(Dispatchers.Default) {
        // 1. Strict Privacy Gate: Never inspect sensitive/banking apps
        if (contextGuard.isBlocked(foregroundPackage)) {
            return@withContext null
        }

        if (screenText.isBlank() || screenText.length < 15) {
            return@withContext null
        }

        // 2. Query On-Device Gemini Nano
        try {
            val sanitizedSample = screenText.take(800).replace("\n", " ")
            val prompt = """
                You are an on-device Android assistant running locally via Gemini Nano.
                Classify the user intent from this visible screen text: "$sanitizedSample"
                App: $foregroundPackage

                Allowed Intents:
                - TRACK_PACKAGE: package tracking number present
                - EXPENSE_LOG: payment, price, receipt, or invoice present
                - NAVIGATION: address, location, or travel destination present
                - WORKFLOW_SHORTCUT: repetitive app navigation detected
                - NONE: casual browsing or unclassifiable

                Return exactly in format:
                INTENT: <ALLOWED_INTENT>
                CONFIDENCE: <0.0 to 1.0>
                PAYLOAD: <extracted entity like tracking number, address, or empty>
            """.trimIndent()

            val response = Generation.getClient().generateContent(prompt)
            val output = response.candidates.firstOrNull()?.text.orEmpty()

            val intent = output.lineSequence()
                .firstOrNull { it.startsWith("INTENT:") }
                ?.substringAfter("INTENT:")?.trim().orEmpty()

            val confidenceStr = output.lineSequence()
                .firstOrNull { it.startsWith("CONFIDENCE:") }
                ?.substringAfter("CONFIDENCE:")?.trim().orEmpty()

            val payload = output.lineSequence()
                .firstOrNull { it.startsWith("PAYLOAD:") }
                ?.substringAfter("PAYLOAD:")?.trim().orEmpty()

            val confidence = confidenceStr.toFloatOrNull() ?: 0.0f

            if (intent == "NONE" || confidence < 0.70f) {
                return@withContext null
            }

            val suggestedAction = when (intent) {
                "TRACK_PACKAGE" -> "open_url"
                "EXPENSE_LOG" -> "launch_app"
                "NAVIGATION" -> "launch_app"
                else -> "shortcut_executed"
            }

            IntentResult(
                intentName = intent,
                confidence = confidence,
                suggestedAction = suggestedAction,
                executionPayload = payload.take(200)
            )
        } catch (_: Exception) {
            // Graceful fallback to local regex heuristics if Gemini Nano is unavailable
            fallbackRegexIntent(screenText)
        }
    }

    /**
     * Deterministic local fallback if AICore / Gemini Nano model is downloading or unsupported.
     */
    private fun fallbackRegexIntent(text: String): IntentResult? {
        val trackingRegex = Regex("""\b(1Z[0-9A-Z]{16}|[0-9]{12}|[0-9]{20,22})\b""")
        val match = trackingRegex.find(text)
        if (match != null) {
            return IntentResult(
                intentName = "TRACK_PACKAGE",
                confidence = 0.95f,
                suggestedAction = "open_url",
                executionPayload = match.value
            )
        }
        return null
    }
}
