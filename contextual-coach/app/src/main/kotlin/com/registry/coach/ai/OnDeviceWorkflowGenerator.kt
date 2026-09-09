package com.registry.coach.ai

import com.google.mlkit.genai.prompt.Generation
import com.registry.coach.data.WorkflowSuggestion
import com.registry.coach.engine.PatternEngine

/** Gemini may describe a proven pattern, but cannot invent executable package targets. */
class OnDeviceWorkflowGenerator {
    suspend fun enrich(base:WorkflowSuggestion,pattern:PatternEngine.Pattern,ephemeralContext:String):WorkflowSuggestion {
        if(ephemeralContext.isBlank()) return base
        return try {
            val chainDesc = if (pattern.chainPackages.size >= 3) {
                pattern.chainPackages.joinToString(" -> ")
            } else {
                "${pattern.fromPackage} -> ${pattern.toPackage}"
            }
            val prompt="""
                You are naming an Android shortcut from verified observations. Do not invent apps, steps, facts, or capabilities.
                Pattern type: ${pattern.kind.name.lowercase()}
                Verified transition: $chainDesc
                Frequency: ${pattern.count}
                Ephemeral visible context: ${ephemeralContext.take(1200)}
                Return exactly two plain-text lines:
                TITLE: a short factual title
                REASON: one sentence grounded only in the verified transition and frequency
            """.trimIndent()
            val response=Generation.getClient().generateContent(prompt)
            val text=response.candidates.firstOrNull()?.text.orEmpty()
            val title=text.lineSequence().firstOrNull { it.startsWith("TITLE:") }?.substringAfter("TITLE:")?.trim().orEmpty()
            val reason=text.lineSequence().firstOrNull { it.startsWith("REASON:") }?.substringAfter("REASON:")?.trim().orEmpty()
            if(title.isBlank()||reason.isBlank()) base else base.copy(title=title.take(80),reason=reason.take(240),generatedBy="gemini_nano")
        } catch (_:Exception) { base }
    }
}
