package com.cslearningos.mobile.feature.assistant.domain

import com.cslearningos.mobile.data.LearningRepository
import com.cslearningos.mobile.feature.assistant.data.KnowledgeAssistantService
import com.cslearningos.mobile.feature.assistant.ui.AssistantCitation
import com.cslearningos.mobile.feature.assistant.ui.AssistantMessage
import com.cslearningos.mobile.ui.AiProviderSettings
import kotlinx.coroutines.flow.first

class KnowledgeAssistantSession(
    private val repository: LearningRepository,
    private val service: KnowledgeAssistantService
) {
    suspend fun findLocalContext(query: String): List<AssistantCitation> {
        val candidates = runCatching { repository.search(query) }
            .getOrDefault(emptyList())
            .map { result ->
                AssistantCitation(
                    id = result.id,
                    type = result.type,
                    title = result.title,
                    excerpt = result.snippet.replace("[", "").replace("]", "")
                )
            }
        return selectAssistantContext(
            candidates.map { citation -> AssistantContextSource(citation.title, citation.excerpt) }
        ).mapNotNull { source ->
            candidates.firstOrNull { candidate ->
                candidate.title == source.title && candidate.excerpt.startsWith(source.excerpt)
            }
                ?.copy(excerpt = source.excerpt)
        }
    }

    suspend fun streamReply(
        settings: AiProviderSettings,
        mode: AssistantRequestMode,
        history: List<AssistantMessage>,
        message: String,
        context: List<AssistantCitation>,
        areas: List<AssistantAreaOption>,
        workingDraft: AssistantWorkingDraft?,
        onDelta: suspend (String) -> Unit
    ) {
        val selectedContext = selectAssistantContext(
            context.map { citation -> AssistantContextSource(citation.title, citation.excerpt) }
        )
        service.streamReply(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            model = settings.model,
            systemPrompt = buildKnowledgeAssistantSystemPrompt(mode, selectedContext, areas, workingDraft),
            userPrompt = buildKnowledgeAssistantUserPrompt(history, message),
            onDelta = onDelta
        )
    }

    suspend fun availableAreas(): List<AssistantAreaOption> {
        val nodes = repository.nodes.first()
        return repository.areas.first().map { area ->
            AssistantAreaOption(
                id = area.id,
                name = area.name,
                exampleTitles = nodes
                    .filter { node -> node.areaId == area.id && node.deletedAt == null }
                    .map { node -> node.title.trim() }
                    .filter { title -> title.isNotBlank() }
                    .take(MaximumAreaExamples)
            )
        }
    }

    suspend fun reviewTopicHints(): List<String> {
        val nodeTitles = repository.nodes.first()
            .map { node -> node.title.trim() }
            .filter { title -> title.isNotBlank() }
            .distinct()
            .take(MaximumReviewTopicHints)
        return if (nodeTitles.isNotEmpty()) nodeTitles else availableAreas().map { it.name }.take(MaximumReviewTopicHints)
    }

    private companion object {
        const val MaximumReviewTopicHints = 6
        const val MaximumAreaExamples = 3
    }
}
