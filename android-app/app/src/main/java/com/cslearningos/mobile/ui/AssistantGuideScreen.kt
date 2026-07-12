package com.cslearningos.mobile.ui

import com.cslearningos.mobile.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AssistantGuideScreen(state: LearningUiState, viewModel: LearningViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GuideHeader(
            configured = state.aiProviderSettings.isConfigured,
            onBack = viewModel::showMore,
            onOpenAssistant = viewModel::showAssistant
        )
        AssistantGuideSection(
            eyebrow = stringResource(R.string.assistant_guide_capabilities_eyebrow),
            title = stringResource(R.string.assistant_guide_capabilities_title),
            items = stringArrayResource(R.array.assistant_guide_capabilities)
        )
        AssistantGuideSection(
            eyebrow = stringResource(R.string.assistant_guide_workflow_eyebrow),
            title = stringResource(R.string.assistant_guide_workflow_title),
            items = stringArrayResource(R.array.assistant_guide_workflow)
        )
        AssistantGuideSection(
            eyebrow = stringResource(R.string.assistant_guide_safety_eyebrow),
            title = stringResource(R.string.assistant_guide_safety_title),
            items = stringArrayResource(R.array.assistant_guide_safety)
        )
        AssistantGuideSection(
            eyebrow = stringResource(R.string.assistant_guide_rollback_eyebrow),
            title = stringResource(R.string.assistant_guide_rollback_title),
            items = stringArrayResource(R.array.assistant_guide_rollback)
        )
    }
}

@Composable
private fun GuideHeader(
    configured: Boolean,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(WorkbenchColors.Surface.copy(alpha = 0.64f))
            .border(BorderStroke(1.dp, WorkbenchColors.Line), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Eyebrow(stringResource(R.string.assistant_guide_eyebrow))
            Text(
                text = stringResource(if (configured) R.string.common_configured else R.string.common_not_configured),
                color = if (configured) WorkbenchColors.Success else WorkbenchColors.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = stringResource(R.string.assistant_guide_title),
            color = WorkbenchColors.InkStrong,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.assistant_guide_intro),
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkbenchButton(
                text = stringResource(R.string.common_back),
                onClick = onBack,
                modifier = Modifier.weight(1f)
            )
            WorkbenchButton(
                text = stringResource(R.string.assistant_guide_open_assistant),
                onClick = onOpenAssistant,
                primary = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AssistantGuideSection(
    eyebrow: String,
    title: String,
    items: Array<String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WorkbenchColors.Surface.copy(alpha = 0.44f))
            .border(BorderStroke(1.dp, WorkbenchColors.Line.copy(alpha = 0.72f)), RoundedCornerShape(16.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Eyebrow(eyebrow)
        Text(title, color = WorkbenchColors.InkStrong, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        items.forEach { item ->
            Text(
                text = "• $item",
                color = WorkbenchColors.Muted,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }
    }
}
