package com.cslearningos.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cslearningos.mobile.R
import com.cslearningos.mobile.feature.sync.SyncReport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SyncSectionContent(state: LearningUiState, viewModel: LearningViewModel) {
    val sync = state.sync
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!sync.isPaired) {
            SyncPairCard(viewModel = viewModel, busy = sync.busy, error = sync.error)
        } else {
            SyncStatusCard(state = state, viewModel = viewModel)
            SyncScopeCard(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun SyncPairCard(viewModel: LearningViewModel, busy: Boolean, error: String?) {
    var endpoint by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var tutorialExpanded by remember { mutableStateOf(true) }
    SyncCard(label = stringResource(R.string.sync_pair_title)) {
        Text(
            text = stringResource(R.string.sync_pair_hint),
            color = WorkbenchColors.Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(WorkbenchColors.SurfaceSoft.copy(alpha = 0.5f))
                .clickable { tutorialExpanded = !tutorialExpanded }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.sync_tutorial_title) + if (tutorialExpanded) " ▲" else " ▼",
                color = WorkbenchColors.AccentStrong,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            if (tutorialExpanded) {
                Text(
                    text = stringResource(R.string.sync_tutorial_step_1),
                    color = WorkbenchColors.InkStrong,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Text(
                    text = stringResource(R.string.sync_tutorial_step_2),
                    color = WorkbenchColors.InkStrong,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Text(
                    text = stringResource(R.string.sync_tutorial_step_3),
                    color = WorkbenchColors.InkStrong,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Text(
                    text = stringResource(R.string.sync_tutorial_step_4),
                    color = WorkbenchColors.InkStrong,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Text(
                    text = stringResource(R.string.sync_tutorial_tip),
                    color = WorkbenchColors.Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
        WorkbenchTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = stringResource(R.string.sync_endpoint_label)
        )
        WorkbenchTextField(
            value = token,
            onValueChange = { token = it },
            label = stringResource(R.string.sync_token_label)
        )
        if (error != null) {
            Text(text = error, color = WorkbenchColors.Danger, fontSize = 12.sp)
        }
        WorkbenchButton(
            text = if (busy) stringResource(R.string.sync_busy) else stringResource(R.string.sync_pair_action),
            onClick = { viewModel.pairSync(endpoint, token) },
            primary = true,
            enabled = !busy && (endpoint.isNotBlank() || token.isNotBlank()),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SyncStatusCard(state: LearningUiState, viewModel: LearningViewModel) {
    val sync = state.sync
    SyncCard(label = stringResource(R.string.more_section_sync_title)) {
        Text(
            text = sync.endpoint,
            color = WorkbenchColors.InkStrong,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(syncReadinessLabel(sync.readiness)),
            color = if (sync.readiness == SyncReadiness.PendingServerPolicy) WorkbenchColors.Muted else WorkbenchColors.AccentStrong,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(
                R.string.sync_server_policy_line,
                if (sync.serverPolicyConfirmed) sync.serverScopes.sorted().joinToString(", ") else stringResource(R.string.sync_server_policy_pending)
            ),
            color = WorkbenchColors.Muted,
            fontSize = 12.sp
        )
        Text(
            text = stringResource(
                R.string.sync_status_last_sync,
                sync.lastSyncAt.takeIf { it > 0 }?.let(::formatSyncTimestamp)
                    ?: stringResource(R.string.sync_status_never)
            ),
            color = WorkbenchColors.Muted,
            fontSize = 12.sp
        )
        sync.lastPullReport?.let { report ->
            val reportArgs = syncPullReportArgs(report)
            Text(
                text = stringResource(
                    R.string.sync_report_pull_line,
                    reportArgs[0],
                    reportArgs[1],
                    reportArgs[2],
                    reportArgs[3]
                ),
                color = WorkbenchColors.Muted,
                fontSize = 12.sp
            )
        }
        sync.lastPushReport?.let { report ->
            Text(
                text = stringResource(R.string.sync_report_push_line, report.totalUploaded, report.rejected),
                color = WorkbenchColors.Muted,
                fontSize = 12.sp
            )
        }
        if (sync.error != null) {
            Text(text = sync.error, color = WorkbenchColors.Danger, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            WorkbenchButton(
                text = if (sync.busy) stringResource(R.string.sync_busy) else stringResource(R.string.sync_pull_now),
                onClick = viewModel::pullSyncNow,
                primary = true,
                enabled = !sync.busy && "sync:read" in sync.serverScopes,
                modifier = Modifier.weight(1f)
            )
            WorkbenchButton(
                text = stringResource(R.string.sync_upload_now),
                onClick = viewModel::uploadSyncNow,
                enabled = !sync.busy && "sync:push" in sync.serverScopes,
                modifier = Modifier.weight(1f)
            )
        }
        WorkbenchButton(
            text = stringResource(R.string.sync_unpair_action),
            onClick = viewModel::unpairSync,
            danger = true,
            enabled = !sync.busy,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SyncScopeCard(state: LearningUiState, viewModel: LearningViewModel) {
    val selected = state.sync.scopeAreas
    SyncCard(label = stringResource(R.string.sync_scope_title)) {
        state.areas.filter { it.deletedAt == null }.forEach { area ->
            val checked = area.id in selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        val next = if (checked) selected - area.id else selected + area.id
                        viewModel.updateSyncScope(next, state.sync.includeDueReviews)
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (checked) "✓" else "○",
                    color = if (checked) WorkbenchColors.AccentStrong else WorkbenchColors.Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = displayAreaName(null, area),
                    color = WorkbenchColors.InkStrong,
                    fontSize = 14.sp
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    viewModel.updateSyncScope(selected, !state.sync.includeDueReviews)
                }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (state.sync.includeDueReviews) "✓" else "○",
                color = if (state.sync.includeDueReviews) WorkbenchColors.AccentStrong else WorkbenchColors.Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.sync_scope_due),
                color = WorkbenchColors.InkStrong,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun SyncCard(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WorkbenchColors.SurfaceCard.copy(alpha = 0.62f))
            .border(BorderStroke(1.dp, WorkbenchColors.Line), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Eyebrow(label)
        content()
    }
}

private fun formatSyncTimestamp(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(SyncTimestampFormatter)

private fun syncReadinessLabel(readiness: SyncReadiness): Int = when (readiness) {
    SyncReadiness.Unpaired -> R.string.sync_readiness_unpaired
    SyncReadiness.PendingServerPolicy -> R.string.sync_readiness_pending
    SyncReadiness.Ready -> R.string.sync_readiness_ready
    SyncReadiness.ReadOnly -> R.string.sync_readiness_read_only
    SyncReadiness.UploadOnly -> R.string.sync_readiness_upload_only
}

internal fun syncPullReportArgs(report: SyncReport): Array<Int> =
    arrayOf(report.pulledNodes, report.pulledQuizzes, report.pulledBiteCards, report.conflicts)

private val SyncTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
