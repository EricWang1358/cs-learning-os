@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.cslearningos.mobile.R
import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.LearningNodeEntity
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val context = LocalContext.current
    val selectedArea = state.areas.firstOrNull { it.id == state.selectedLibraryAreaId }
    val folders = buildLibraryRootFolders(state.areas, state.nodes, state.dueQuizzes, context)
    val overview = buildLibraryOverview(state.areas, state.nodes, context)
    val map = buildLibraryMap(state.areas, state.nodes, context)
    val detail = selectedArea?.let {
        buildLibraryAreaDetail(it, state.nodes, state.dueQuizzes, state.libraryCheckedFilter, context)
    }

    var showCreateAreaDialog by rememberSaveable { mutableStateOf(state.areas.isEmpty()) }
    var createAreaDraft by rememberSaveable { mutableStateOf("") }
    var renameAreaId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameAreaDraft by rememberSaveable { mutableStateOf("") }
    var moveNodeId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (detail == null) {
            LibraryRootScreen(
                folders = folders,
                overview = overview,
                map = map,
                onOpenArea = viewModel::openLibraryArea,
                onCreateArea = {
                    createAreaDraft = ""
                    showCreateAreaDialog = true
                },
                onRenameArea = { area ->
                    renameAreaId = area.id
                    renameAreaDraft = area.name
                },
                onDeleteArea = { area -> viewModel.deleteArea(area.id) },
                state = state
            )
        } else {
            LibraryAreaDetailScreen(
                area = selectedArea,
                detail = detail,
                state = state,
                onBack = viewModel::closeLibraryArea,
                onNewNode = { viewModel.startNewNode(selectedArea.id) },
                onRenameArea = {
                    renameAreaId = selectedArea.id
                    renameAreaDraft = selectedArea.name
                },
                onDeleteArea = { viewModel.deleteArea(selectedArea.id) },
                onSetFilter = viewModel::setLibraryCheckedFilter,
                onOpenNode = { viewModel.openNode(it) },
                onEditNode = { viewModel.editNode(it) },
                onToggleChecked = { viewModel.toggleNodeChecked(it.id) },
                onMoveNode = { node -> moveNodeId = node.id }
            )
        }
    }

    if (showCreateAreaDialog) {
        LibraryTextDialog(
            title = stringResource(R.string.library_create_area_title),
            value = createAreaDraft,
            onValueChange = { createAreaDraft = it },
            onDismiss = {
                createAreaDraft = ""
                showCreateAreaDialog = false
            },
            onConfirm = {
                viewModel.createArea(createAreaDraft)
                createAreaDraft = ""
                showCreateAreaDialog = false
            },
            confirmLabel = stringResource(R.string.library_create_area_confirm)
        )
    }

    val renameArea = state.areas.firstOrNull { it.id == renameAreaId }
    if (renameArea != null) {
        LibraryTextDialog(
            title = stringResource(R.string.library_rename_area_title),
            value = renameAreaDraft,
            onValueChange = { renameAreaDraft = it },
            onDismiss = {
                renameAreaId = null
                renameAreaDraft = ""
            },
            onConfirm = {
                viewModel.renameArea(renameArea.id, renameAreaDraft)
                renameAreaId = null
                renameAreaDraft = ""
            },
            confirmLabel = stringResource(R.string.library_rename_area_confirm)
        )
    }

    val movingNode = state.nodes.firstOrNull { it.id == moveNodeId }
    if (movingNode != null) {
        AlertDialog(
            onDismissRequest = { moveNodeId = null },
            title = { Text(stringResource(R.string.library_move_node_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.areas
                        .filter { it.deletedAt == null && it.id != movingNode.areaId }
                        .forEach { area ->
                            TextButton(
                                onClick = {
                                    viewModel.moveNodeToArea(movingNode.id, area.id)
                                    moveNodeId = null
                                }
                            ) {
                                Text(displayAreaName(context, area))
                            }
                        }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { moveNodeId = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun LibraryRootScreen(
    folders: List<LibraryFolderRow>,
    overview: LibraryOverview,
    map: LibraryMap,
    onOpenArea: (String) -> Unit,
    onCreateArea: () -> Unit,
    onRenameArea: (AreaEntity) -> Unit,
    onDeleteArea: (AreaEntity) -> Unit,
    state: LearningUiState
) {
    val context = LocalContext.current

    ToolbarRow {
        WorkbenchButton(
            text = stringResource(R.string.library_create_area_button),
            onClick = onCreateArea,
            primary = true
        )
    }

    if (folders.isEmpty()) {
        EmptyWorkbenchCard(
            title = stringResource(R.string.library_root_empty_title),
            body = stringResource(R.string.library_root_empty_body)
        )
    }

    folders.forEach { folder ->
        val area = state.areas.firstOrNull { it.id == folder.areaId } ?: return@forEach
        InteractiveCard(onClick = { onOpenArea(folder.areaId) }, accent = false) {
            Eyebrow(stringResource(R.string.library_folder_eyebrow))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(folder.title, color = WorkbenchColors.InkStrong, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    if (folder.trackPreview.isNotBlank()) {
                        Text(
                            text = folder.trackPreview,
                            color = WorkbenchColors.Muted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaPill(stringResource(R.string.common_nodes), folder.nodeCount.toString())
                    MetaPill(stringResource(R.string.library_checked_filter), folder.checkedCount.toString())
                    if (folder.dueCount > 0) {
                        MetaPill(stringResource(R.string.common_due), folder.dueCount.toString())
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkbenchButton(stringResource(R.string.common_open), { onOpenArea(folder.areaId) }, primary = true)
                WorkbenchButton(stringResource(R.string.common_edit), { onRenameArea(area) })
                WorkbenchButton(stringResource(R.string.common_delete), { onDeleteArea(area) }, danger = true)
            }
        }
    }

    CollapsibleWorkbenchSection(
        eyebrow = stringResource(R.string.library_overview_eyebrow),
        title = stringResource(R.string.library_overview_title),
        body = stringResource(R.string.library_overview_body_folder),
        expandLabel = stringResource(R.string.common_open),
        collapseLabel = stringResource(R.string.common_close),
        initiallyExpanded = false
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetaPill(stringResource(R.string.library_areas_label), overview.areaCount.toString(), Modifier.weight(1f))
            MetaPill(stringResource(R.string.common_nodes), overview.nodeCount.toString(), Modifier.weight(1f))
            MetaPill(stringResource(R.string.library_checked_filter), overview.checkedCount.toString(), Modifier.weight(1f))
        }
        MetaPill(stringResource(R.string.library_featured_label), overview.featuredAreaLabel)
    }

    CollapsibleWorkbenchSection(
        eyebrow = stringResource(R.string.library_map_eyebrow),
        title = stringResource(R.string.library_map_title),
        body = stringResource(R.string.library_map_body_folder),
        expandLabel = stringResource(R.string.common_open),
        collapseLabel = stringResource(R.string.common_close),
        initiallyExpanded = false
    ) {
        map.areas.forEach { area ->
            InteractiveCard(onClick = { onOpenArea(area.areaId) }, accent = false) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(area.label, color = WorkbenchColors.InkStrong, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        if (area.trackPreview.isNotBlank()) {
                            Text(area.trackPreview, color = WorkbenchColors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetaPill(stringResource(R.string.common_nodes), area.nodeCount.toString())
                        MetaPill(stringResource(R.string.library_checked_filter), area.checkedCount.toString())
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryAreaDetailScreen(
    area: AreaEntity,
    detail: LibraryAreaDetail,
    state: LearningUiState,
    onBack: () -> Unit,
    onNewNode: () -> Unit,
    onRenameArea: () -> Unit,
    onDeleteArea: () -> Unit,
    onSetFilter: (LibraryCheckedFilter) -> Unit,
    onOpenNode: (LearningNodeEntity) -> Unit,
    onEditNode: (LearningNodeEntity) -> Unit,
    onToggleChecked: (LearningNodeEntity) -> Unit,
    onMoveNode: (LearningNodeEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WorkbenchCard {
            Eyebrow(stringResource(R.string.library_folder_eyebrow))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(detail.title, color = WorkbenchColors.InkStrong, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetaPill(stringResource(R.string.common_nodes), detail.nodeCount.toString())
                        MetaPill(stringResource(R.string.library_checked_filter), detail.checkedCount.toString())
                    }
                }
                WorkbenchButton(stringResource(R.string.library_back_to_areas), onBack)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkbenchButton(stringResource(R.string.library_create_in_area_button), onNewNode, primary = true)
                WorkbenchButton(stringResource(R.string.common_edit), onRenameArea)
                WorkbenchButton(stringResource(R.string.common_delete), onDeleteArea, danger = true)
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkbenchButton(
                text = stringResource(R.string.library_filter_all),
                onClick = { onSetFilter(LibraryCheckedFilter.All) },
                primary = state.libraryCheckedFilter == LibraryCheckedFilter.All
            )
            WorkbenchButton(
                text = stringResource(R.string.library_filter_checked),
                onClick = { onSetFilter(LibraryCheckedFilter.Checked) },
                primary = state.libraryCheckedFilter == LibraryCheckedFilter.Checked
            )
        }

        if (detail.items.isEmpty()) {
            EmptyWorkbenchCard(
                title = stringResource(R.string.library_area_empty_title),
                body = stringResource(
                    if (state.libraryCheckedFilter == LibraryCheckedFilter.All) {
                        R.string.library_area_empty_body
                    } else {
                        R.string.library_checked_empty_body
                    }
                )
            )
        }

        detail.items.forEach { item ->
            InteractiveCard(onClick = { onOpenNode(item.node) }, accent = item.checked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(
                        checked = item.checked,
                        onCheckedChange = { onToggleChecked(item.node) }
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetaPill(stringResource(R.string.library_track_badge), item.trackLabel)
                            if (item.dueCount > 0) {
                                MetaPill(stringResource(R.string.common_due), item.dueCount.toString())
                            }
                        }
                        Text(
                            text = item.title,
                            color = WorkbenchColors.InkStrong,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.summary,
                            color = WorkbenchColors.Muted,
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WorkbenchButton(stringResource(R.string.common_read), { onOpenNode(item.node) }, primary = true)
                            WorkbenchButton(stringResource(R.string.common_edit), { onEditNode(item.node) })
                            WorkbenchButton(stringResource(R.string.library_move_node_button), { onMoveNode(item.node) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTextDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            WorkbenchTextField(
                value = value,
                onValueChange = onValueChange,
                label = title
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

fun formatTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))
