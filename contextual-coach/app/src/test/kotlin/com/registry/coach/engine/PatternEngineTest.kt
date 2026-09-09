package com.registry.coach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternEngineTest {
    private val engine=PatternEngine()
    @Test fun `requires three repeated transitions`() {
        val events=listOf(
            event("mail.app",0),event("maps.app",10),event("mail.app",200),event("maps.app",210),event("mail.app",400),event("maps.app",412)
        )
        val patterns=engine.detect(events)
        assertEquals(1,patterns.size);assertEquals(3,patterns.first().count);assertEquals("mail.app",patterns.first().fromPackage)
    }
    @Test fun `filters sensitive applications before a pattern exists`() {
        val events=listOf(event("mail.app",0),event("secure.bank.app",10),event("mail.app",200),event("secure.bank.app",210),event("mail.app",400),event("secure.bank.app",410))
        assertTrue(engine.detect(events).isEmpty())
    }

    @Test fun `trampoline filter ignores launcher and keyboards between transitions`() {
        val events = listOf(
            event("com.whatsapp", 0),
            event("com.sec.android.app.launcher", 2),
            event("com.google.android.youtube", 5),
            event("com.whatsapp", 200),
            event("com.samsung.android.honeyboard", 202),
            event("com.sec.android.app.launcher", 204),
            event("com.google.android.youtube", 207),
            event("com.whatsapp", 400),
            event("com.sec.android.app.launcher", 402),
            event("com.google.android.youtube", 406)
        )
        val patterns = engine.detect(events)
        assertEquals(1, patterns.size)
        assertEquals("com.whatsapp", patterns.first().fromPackage)
        assertEquals("com.google.android.youtube", patterns.first().toPackage)
        assertEquals(3, patterns.first().count)
    }

    @Test fun `detects thrashing friction loops when alternating back and forth`() {
        val events = listOf(
            event("app.calc", 0),
            event("app.notes", 5),
            event("app.calc", 10),
            event("app.notes", 15),
            event("app.calc", 20),
            event("app.notes", 25),
            event("app.calc", 30)
        )
        val patterns = engine.detect(events)
        val thrashing = patterns.firstOrNull { it.kind == PatternEngine.PatternKind.THRASHING }
        org.junit.Assert.assertNotNull("Thrashing pattern must be detected", thrashing)
        assertEquals(6, thrashing!!.count)
        val suggestion = engine.suggestion(thrashing)
        assertTrue(suggestion.title.startsWith("Split-Screen:"))
        assertEquals("thrashing", suggestion.patternType)
        assertEquals(2, suggestion.actions.size)
        assertEquals("adjacent", suggestion.actions[1].params["mode"])
    }

    @Test fun `detects 3-step chains A to B to C`() {
        val events = listOf(
            event("app.camera", 0), event("app.photos", 5), event("app.social", 12),
            event("app.camera", 300), event("app.photos", 306), event("app.social", 314),
            event("app.camera", 600), event("app.photos", 605), event("app.social", 611)
        )
        val patterns = engine.detect(events)
        val chain = patterns.firstOrNull { it.kind == PatternEngine.PatternKind.CHAIN }
        org.junit.Assert.assertNotNull("3-step chain must be detected", chain)
        assertEquals(3, chain!!.count)
        assertEquals(listOf("app.camera", "app.photos", "app.social"), chain.chainPackages)
        val suggestion = engine.suggestion(chain)
        assertEquals("chain", suggestion.patternType)
        assertEquals(2, suggestion.actions.size)
        assertEquals("app.photos", suggestion.actions[0].params["package"])
        assertEquals("app.social", suggestion.actions[1].params["package"])
    }

    @Test fun `detects macro-routines across distinct sessions as habits`() {
        val history = listOf(
            PatternEngine.TransitionRecord("app.music", "app.maps", 1_000_000L, 10_000L),
            PatternEngine.TransitionRecord("app.music", "app.maps", 1_400_000L, 12_000L),
            PatternEngine.TransitionRecord("app.music", "app.maps", 1_800_000L, 8_000L)
        )
        val patterns = engine.detect(events = emptyList(), historicTransitions = history)
        assertEquals(1, patterns.size)
        val habit = patterns.first()
        assertEquals(PatternEngine.PatternKind.HABIT, habit.kind)
        assertEquals(3, habit.count)
        val suggestion = engine.suggestion(habit)
        assertEquals("habit", suggestion.patternType)
        assertTrue(suggestion.reason.contains("across 3 sessions"))
    }

    @Test fun `rule 4 invariant - chains and habits below 3 occurrences are never detected`() {
        val twoChains = listOf(
            event("app.camera", 0), event("app.photos", 5), event("app.social", 12),
            event("app.camera", 300), event("app.photos", 306), event("app.social", 314)
        )
        val chainPatterns = engine.detect(twoChains)
        assertTrue("Chains below 3 occurrences must not be detected", chainPatterns.none { it.kind == PatternEngine.PatternKind.CHAIN })

        val twoHabits = listOf(
            PatternEngine.TransitionRecord("app.music", "app.maps", 1_000_000L, 10_000L),
            PatternEngine.TransitionRecord("app.music", "app.maps", 1_400_000L, 12_000L)
        )
        val habitPatterns = engine.detect(events = emptyList(), historicTransitions = twoHabits)
        assertTrue("Habits below 3 occurrences must not be detected", habitPatterns.isEmpty())
    }

    private fun event(packageName:String,seconds:Long)=PatternEngine.Event(packageName,seconds*1000)
}
