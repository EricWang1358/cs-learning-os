package com.cslearningos.mobile.feature.capture.domain

import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.feature.assistant.domain.AssistantAreaOption

fun captureAssistantAreaOptions(
    areas: List<AreaEntity>,
    nodes: List<LearningNodeEntity>
): List<AssistantAreaOption> =
    areas.filter { area -> area.deletedAt == null }.map { area ->
        AssistantAreaOption(
            id = area.id,
            name = area.name,
            exampleTitles = nodes
                .filter { node -> node.deletedAt == null && node.areaId == area.id }
                .map { node -> node.title.trim() }
                .filter { title -> title.isNotBlank() }
                .take(MaximumAreaExamples)
        )
    }

private const val MaximumAreaExamples = 3
