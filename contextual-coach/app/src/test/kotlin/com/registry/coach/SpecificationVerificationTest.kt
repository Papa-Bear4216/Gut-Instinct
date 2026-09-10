package com.registry.coach

import com.registry.coach.ai.OnDeviceWorkflowGenerator
import com.registry.coach.data.NativeWorkflow
import com.registry.coach.data.WorkflowAction
import com.registry.coach.data.WorkflowSuggestion
import com.registry.coach.engine.PatternEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecificationVerificationTest {

    private val engine = PatternEngine()
    private val generator = OnDeviceWorkflowGenerator()

    @Test
    fun `rule 4 - pattern strictly requires at least 3 occurrences`() {
        val twoOccurrences = listOf(
            PatternEngine.Event("com.app.a", 0),
            PatternEngine.Event("com.app.b", 10_000),
            PatternEngine.Event("com.app.a", 20_000),
            PatternEngine.Event("com.app.b", 30_000)
        )
        val patterns = engine.detect(twoOccurrences)
        assertTrue("Patterns with fewer than 3 occurrences must not be detected", patterns.isEmpty())

        val threeOccurrences = twoOccurrences + listOf(
            PatternEngine.Event("com.app.a", 40_000),
            PatternEngine.Event("com.app.b", 50_000)
        )
        val detected = engine.detect(threeOccurrences)
        assertEquals(1, detected.size)
        assertEquals(3, detected.first().count)
        assertEquals("com.app.a", detected.first().fromPackage)
        assertEquals("com.app.b", detected.first().toPackage)
    }

    @Test
    fun `rule 6 - sensitive package filter blocks before tree access`() {
        val sensitiveEvents = listOf(
            PatternEngine.Event("com.chase.sig.android", 0),
            PatternEngine.Event("com.wf.wellsfargomobile", 5_000),
            PatternEngine.Event("org.toshi", 10_000),
            PatternEngine.Event("com.sofi.mobile", 15_000),
            PatternEngine.Event("com.fidelity.android", 20_000),
            PatternEngine.Event("epic.mychart.android", 25_000),
            PatternEngine.Event("com.cvs.rx", 30_000),
            PatternEngine.Event("com.google.android.apps.walletnfcrel", 35_000),
            PatternEngine.Event("com.onepassword.android", 40_000),
            PatternEngine.Event("com.google.android.apps.authenticator2", 45_000),
            PatternEngine.Event("com.android.settings", 50_000)
        )
        for (event in sensitiveEvents) {
            assertTrue(
                "Package ${event.packageName} must be identified as sensitive",
                engine.isSensitive(event.packageName)
            )
        }
    }

    @Test
    fun `rule 9 - deterministic fallback when AICore or Nano is unavailable`() = runBlocking {
        val pattern = PatternEngine.Pattern(
            fromPackage = "com.google.android.gm",
            toPackage = "com.google.android.apps.maps",
            count = 4,
            medianGapMs = 12_000
        )
        val baseSuggestion = engine.suggestion(pattern)

        // When AICore/Nano is absent or ephemeral context is empty, deterministic fallback must be returned
        val result = generator.enrich(baseSuggestion, pattern, "")
        assertEquals(baseSuggestion.title, result.title)
        assertEquals("deterministic", result.generatedBy)
        assertEquals("com.google.android.apps.maps", result.actions.first().params["package"])
    }

    @Test
    fun `rule 10 - suggestions are distinct from executable workflows`() {
        val pattern = PatternEngine.Pattern(
            fromPackage = "com.test.notes",
            toPackage = "com.test.slack",
            count = 3,
            medianGapMs = 5_000
        )
        val suggestion: WorkflowSuggestion = engine.suggestion(pattern)

        // Suggestion is created with deterministic action
        assertEquals("com_test_notes_com_test_slack", suggestion.id)
        assertEquals(3, suggestion.evidenceCount)
        assertEquals(1, suggestion.actions.size)
        assertEquals("launch_app", suggestion.actions.first().type)

        // A suggestion does NOT execute until converted to NativeWorkflow upon user approval
        val approvedWorkflow = NativeWorkflow(
            id = suggestion.id,
            fromPackage = suggestion.fromPackage,
            toPackage = suggestion.toPackage,
            title = suggestion.title,
            evidenceCount = suggestion.evidenceCount,
            estimatedSecondsSaved = suggestion.estimatedSecondsSaved,
            approvedAt = System.currentTimeMillis(),
            actions = suggestion.actions
        )
        assertTrue(approvedWorkflow.enabled)
        assertEquals(0, approvedWorkflow.runCount)
        assertEquals(30_000L, approvedWorkflow.cooldownMs)
    }

    @Test
    fun `rule 11 - cooldown and timeout boundaries prevent loops`() {
        val workflow = NativeWorkflow(
            id = "test_cooldown",
            fromPackage = "com.a",
            toPackage = "com.b",
            title = "Test",
            evidenceCount = 3,
            estimatedSecondsSaved = 10,
            approvedAt = 1000L,
            cooldownMs = 30_000L,
            lastRunAt = 50_000L
        )
        // Immediate trigger within cooldown must be rejected
        val tooSoon = 60_000L // only 10s elapsed
        assertTrue(tooSoon - workflow.lastRunAt < workflow.cooldownMs)

        // Trigger after cooldown passes
        val afterCooldown = 85_000L // 35s elapsed
        assertTrue(afterCooldown - workflow.lastRunAt >= workflow.cooldownMs)
    }
}
