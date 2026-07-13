package com.cslearningos.mobile.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRunMachineTest {
    @Test
    fun runAdvancesThroughContextModelAndParsingEffects() {
        val runId = AssistantRunId("run-1")

        val started = AssistantRunMachine.reduce(
            AssistantRunState.Idle,
            AssistantRunEvent.Start(runId)
        )
        assertEquals(AssistantRunState.BuildingContext(runId), started.state)
        assertEquals(listOf(AssistantRunEffect.BuildContext(runId)), started.effects)

        val streaming = AssistantRunMachine.reduce(
            started.state,
            AssistantRunEvent.ContextReady(runId)
        )
        assertEquals(AssistantRunState.Streaming(runId), streaming.state)
        assertEquals(listOf(AssistantRunEffect.StartModel(runId)), streaming.effects)

        val parsing = AssistantRunMachine.reduce(
            streaming.state,
            AssistantRunEvent.ModelCompleted(runId)
        )
        assertEquals(AssistantRunState.Parsing(runId), parsing.state)
        assertEquals(listOf(AssistantRunEffect.ParseResult(runId)), parsing.effects)

        val completed = AssistantRunMachine.reduce(
            parsing.state,
            AssistantRunEvent.ParseCompleted(runId)
        )
        assertEquals(AssistantRunState.Completed(runId), completed.state)
    }

    @Test
    fun staleRunEventsCannotAdvanceActiveRun() {
        val active = AssistantRunState.Streaming(AssistantRunId("run-new"))

        val transition = AssistantRunMachine.reduce(
            active,
            AssistantRunEvent.ModelCompleted(AssistantRunId("run-old"))
        )

        assertEquals(active, transition.state)
        assertTrue(transition.effects.isEmpty())
    }

    @Test
    fun outOfOrderEventCannotAdvanceMatchingRun() {
        val runId = AssistantRunId("run-1")
        val active = AssistantRunState.Streaming(runId)

        val transition = AssistantRunMachine.reduce(
            active,
            AssistantRunEvent.ContextReady(runId)
        )

        assertEquals(active, transition.state)
        assertTrue(transition.effects.isEmpty())
    }

    @Test
    fun modelRequestRequiresAtLeastOneMessage() {
        val failure = runCatching {
            ModelRequest(AssistantRunId("run-1"), emptyList())
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }
}
