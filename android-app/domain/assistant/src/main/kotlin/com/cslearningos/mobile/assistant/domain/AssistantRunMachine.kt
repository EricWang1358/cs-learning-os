package com.cslearningos.mobile.assistant.domain

sealed interface AssistantRunState {
    data object Idle : AssistantRunState
    data class BuildingContext(val runId: AssistantRunId) : AssistantRunState
    data class Streaming(val runId: AssistantRunId) : AssistantRunState
    data class Parsing(val runId: AssistantRunId) : AssistantRunState
    data class Completed(val runId: AssistantRunId) : AssistantRunState
    data class Failed(val runId: AssistantRunId, val code: String) : AssistantRunState
    data class Cancelled(val runId: AssistantRunId) : AssistantRunState
    data class Superseded(val runId: AssistantRunId) : AssistantRunState
}

sealed interface AssistantRunEvent {
    data class Start(val runId: AssistantRunId) : AssistantRunEvent
    data class ContextReady(val runId: AssistantRunId) : AssistantRunEvent
    data class ModelCompleted(val runId: AssistantRunId) : AssistantRunEvent
    data class ParseCompleted(val runId: AssistantRunId) : AssistantRunEvent
    data class Fail(val runId: AssistantRunId, val code: String) : AssistantRunEvent
    data class Cancel(val runId: AssistantRunId) : AssistantRunEvent
    data class Supersede(val runId: AssistantRunId) : AssistantRunEvent
}

sealed interface AssistantRunEffect {
    data class BuildContext(val runId: AssistantRunId) : AssistantRunEffect
    data class StartModel(val runId: AssistantRunId) : AssistantRunEffect
    data class ParseResult(val runId: AssistantRunId) : AssistantRunEffect
}

data class AssistantRunTransition(
    val state: AssistantRunState,
    val effects: List<AssistantRunEffect> = emptyList()
)

object AssistantRunMachine {
    fun reduce(state: AssistantRunState, event: AssistantRunEvent): AssistantRunTransition {
        if (event !is AssistantRunEvent.Start && state.runIdOrNull() != event.runId()) {
            return AssistantRunTransition(state)
        }

        return when (event) {
            is AssistantRunEvent.Start -> when (state) {
                AssistantRunState.Idle,
                is AssistantRunState.Completed,
                is AssistantRunState.Failed,
                is AssistantRunState.Cancelled,
                is AssistantRunState.Superseded -> AssistantRunTransition(
                    state = AssistantRunState.BuildingContext(event.runId),
                    effects = listOf(AssistantRunEffect.BuildContext(event.runId))
                )
                else -> AssistantRunTransition(state)
            }

            is AssistantRunEvent.ContextReady -> if (state is AssistantRunState.BuildingContext) {
                AssistantRunTransition(
                    state = AssistantRunState.Streaming(event.runId),
                    effects = listOf(AssistantRunEffect.StartModel(event.runId))
                )
            } else {
                AssistantRunTransition(state)
            }

            is AssistantRunEvent.ModelCompleted -> if (state is AssistantRunState.Streaming) {
                AssistantRunTransition(
                    state = AssistantRunState.Parsing(event.runId),
                    effects = listOf(AssistantRunEffect.ParseResult(event.runId))
                )
            } else {
                AssistantRunTransition(state)
            }

            is AssistantRunEvent.ParseCompleted -> if (state is AssistantRunState.Parsing) {
                AssistantRunTransition(AssistantRunState.Completed(event.runId))
            } else {
                AssistantRunTransition(state)
            }

            is AssistantRunEvent.Fail -> if (state.isActive()) {
                AssistantRunTransition(AssistantRunState.Failed(event.runId, event.code))
            } else {
                AssistantRunTransition(state)
            }

            is AssistantRunEvent.Cancel -> if (state.isActive()) {
                AssistantRunTransition(AssistantRunState.Cancelled(event.runId))
            } else {
                AssistantRunTransition(state)
            }

            is AssistantRunEvent.Supersede -> if (state.isActive()) {
                AssistantRunTransition(AssistantRunState.Superseded(event.runId))
            } else {
                AssistantRunTransition(state)
            }
        }
    }
}

private fun AssistantRunState.isActive(): Boolean =
    this is AssistantRunState.BuildingContext ||
        this is AssistantRunState.Streaming ||
        this is AssistantRunState.Parsing

private fun AssistantRunState.runIdOrNull(): AssistantRunId? = when (this) {
    AssistantRunState.Idle -> null
    is AssistantRunState.BuildingContext -> runId
    is AssistantRunState.Streaming -> runId
    is AssistantRunState.Parsing -> runId
    is AssistantRunState.Completed -> runId
    is AssistantRunState.Failed -> runId
    is AssistantRunState.Cancelled -> runId
    is AssistantRunState.Superseded -> runId
}

private fun AssistantRunEvent.runId(): AssistantRunId = when (this) {
    is AssistantRunEvent.Start -> runId
    is AssistantRunEvent.ContextReady -> runId
    is AssistantRunEvent.ModelCompleted -> runId
    is AssistantRunEvent.ParseCompleted -> runId
    is AssistantRunEvent.Fail -> runId
    is AssistantRunEvent.Cancel -> runId
    is AssistantRunEvent.Supersede -> runId
}
