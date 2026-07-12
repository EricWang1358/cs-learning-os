package com.cslearningos.mobile.feature.assistant.domain

enum class AssistantTool {
    LocalSearch,
    OpenEditableDraft,
    SaveCapture,
    SaveDailyReview
}

data class AssistantToolPolicy(
    val requiresConfirmation: Boolean
)

data class AssistantContextSource(
    val title: String,
    val excerpt: String
)

data class AssistantAreaOption(
    val id: String,
    val name: String,
    val exampleTitles: List<String> = emptyList()
)

enum class AssistantRequestMode {
    Answer,
    Draft,
    ReviewQuestion,
    ReviewEvaluation
}

object AssistantSafetyLimits {
    const val MaximumContextItems = 3
    const val MaximumContextCharacters = 1_200
    const val MaximumExcerptCharacters = 420
}

fun assistantToolPolicy(tool: AssistantTool): AssistantToolPolicy =
    AssistantToolPolicy(
        requiresConfirmation = tool != AssistantTool.LocalSearch
    )

fun selectAssistantContext(sources: List<AssistantContextSource>): List<AssistantContextSource> {
    var remainingCharacters = AssistantSafetyLimits.MaximumContextCharacters
    return sources
        .asSequence()
        .map { source ->
            source.copy(
                title = source.title.trim(),
                excerpt = source.excerpt.trim().take(AssistantSafetyLimits.MaximumExcerptCharacters)
            )
        }
        .filter { it.title.isNotBlank() }
        .mapNotNull { source ->
            if (remainingCharacters <= 0) {
                null
            } else {
                val excerpt = source.excerpt.take(remainingCharacters)
                remainingCharacters -= excerpt.length
                source.copy(excerpt = excerpt)
            }
        }
        .take(AssistantSafetyLimits.MaximumContextItems)
        .toList()
}

fun assistantRequestModeFor(message: String): AssistantRequestMode {
    val normalized = message.trim().lowercase()
    val draftSignals = listOf(
        "新建",
        "创建",
        "建立",
        "生成笔记",
        "创建笔记",
        "新建笔记",
        "整理成笔记",
        "构建笔记",
        "create a note",
        "create note",
        "draft a note",
        "make a note"
    )
    return if (draftSignals.any(normalized::contains)) {
        AssistantRequestMode.Draft
    } else {
        AssistantRequestMode.Answer
    }
}
