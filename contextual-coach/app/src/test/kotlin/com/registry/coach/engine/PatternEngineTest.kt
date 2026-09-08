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
    private fun event(packageName:String,seconds:Long)=PatternEngine.Event(packageName,seconds*1000)
}
