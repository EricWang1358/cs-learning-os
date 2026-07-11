@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
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
import com.cslearningos.mobile.data.QuizItemEntity
import java.text.DateFormat
import java.util.Date

private data class LibraryScreenState(
    val areas: List<AreaEntity>,
    val nodes: List<LearningNodeEntity>,
    val trashNodes: List<LearningNodeEntity>,
    val dueQuizzes: List<QuizItemEntity>,
    val selectedAreaId: String?,
    val checkedFilter: LibraryCheckedFilter
)

private object LibraryLayoutTokens {
    val FolderMetricWidth = 58.dp
    val FolderMetricHeight = 36.dp
    const val FolderActionGroupWidthFraction = 0.76f
    val FolderActionGap = 8.dp
}

@Composable
fun LibraryScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val screenState = state.toLibraryScreenState()
    val context = LocalContext.current
    val root = buildLibraryRootModel(screenState.areas, screenState.nodes, screenState.dueQuizzes, context)

    var showCreateAreaDialog by rememberSaveable { mutableStateOf(screenState.areas.isEmpty()) }
    var createAreaDraft by rememberSaveable { mutableStateOf("") }
    var renameAreaId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameAreaDraft by rememberSaveable { mutableStateOf("") }
    var moveNodeId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LibraryRootScreen(
                folders = root.folders,
                selectedAreaId = screenState.selectedAreaId,
                nodes = screenState.nodes,
                trashNodes = screenState.trashNodes,
                dueQuizzes = screenState.dueQuizzes,
                checkedFilter = screenState.checkedFilter,
                onToggleArea = viewModel::toggleLibraryArea,
                onNewNode = viewModel::startNewNode,
                onCreateArea = {
                    createAreaDraft = ""
                    showCreateAreaDialog = true
                },
                onRenameArea = { area ->
                    renameAreaId = area.id
                    renameAreaDraft = area.name
                },
                onDeleteArea = { area -> viewModel.deleteArea(area.id) },
                onOpenNode = viewModel::openNode,
                onSetFilter = viewModel::setLibraryCheckedFilter,
                onRestoreNode = viewModel::restoreNode,
                onDeleteForever = viewModel::permanentlyDeleteNode,
                areas = screenState.areas
            )
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

    val renameArea = screenState.areas.firstOrNull { it.id == renameAreaId }
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

    val movingNode = screenState.nodes.firstOrNull { it.id == moveNodeId }
    if (movingNode != null) {
        AlertDialog(
            onDismissRequest = { moveNodeId = null },
            title = { Text(stringResource(R.string.library_move_node_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    screenState.areas
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
    selectedAreaId: String?,
    nodes: List<LearningNodeEntity>,
    trashNodes: List<LearningNodeEntity>,
    dueQuizzes: List<QuizItemEntity>,
    checkedFilter: LibraryCheckedFilter,
    onToggleArea: (String) -> Unit,
    onNewNode: (String) -> Unit,
    onCreateArea: () -> Unit,
    onRenameArea: (AreaEntity) -> Unit,
    onDeleteArea: (AreaEntity) -> Unit,
    onOpenNode: (LearningNodeEntity) -> Unit,
    onSetFilter: (LibraryCheckedFilter) -> Unit,
    onRestoreNode: (LearningNodeEntity) -> Unit,
    onDeleteForever: (LearningNodeEntity) -> Unit,
    areas: List<AreaEntity>
) {
    val context = LocalContext.current

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
        val area = areas.firstOrNull { it.id == folder.areaId } ?: return@forEach
        WorkbenchCard(accent = false) {
            Column(
                modifier = Modifier.fillMaxWidth().clickable { onToggleArea(folder.areaId) },
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    MetaPill(stringResource(R.string.common_nodes), folder.nodeCount.toString(), Modifier.width(LibraryLayoutTokens.FolderMetricWidth).height(LibraryLayoutTokens.FolderMetricHeight))
                    MetaPill(stringResource(R.string.library_checked_filter), folder.checkedCount.toString(), Modifier.width(LibraryLayoutTokens.FolderMetricWidth).height(LibraryLayoutTokens.FolderMetricHeight))
                    if (folder.dueCount > 0) {
                        MetaPill(stringResource(R.string.common_due), folder.dueCount.toString(), Modifier.width(LibraryLayoutTokens.FolderMetricWidth).height(LibraryLayoutTokens.FolderMetricHeight))
                    }
                }
            }
            if (selectedAreaId == folder.areaId) {
                val detail = buildLibraryAreaDetail(area, nodes, dueQuizzes, checkedFilter, context)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onNewNode(folder.areaId) }) { Text(stringResource(R.string.library_create_in_area_button)) }
                    TextButton(onClick = { onRenameArea(area) }) { Text(stringResource(R.string.common_edit)) }
                    TextButton(onClick = { onDeleteArea(area) }) { Text(stringResource(R.string.common_delete)) }
                    TextButton(onClick = { onSetFilter(LibraryCheckedFilter.All) }) { Text(stringResource(R.string.library_filter_all)) }
                    TextButton(onClick = { onSetFilter(LibraryCheckedFilter.Checked) }) { Text(stringResource(R.string.library_filter_checked)) }
                }
                detail.items.forEach { item ->
                    Column(modifier = Modifier.fillMaxWidth().clickable { onOpenNode(item.node) }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.title, color = WorkbenchColors.InkStrong, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${item.trackLabel} · ${item.summary}", color = WorkbenchColors.Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    var trashExpanded by rememberSaveable { mutableStateOf(false) }
    var deleteForeverNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    WorkbenchCard(accent = false) {
        Column(modifier = Modifier.fillMaxWidth().clickable { trashExpanded = !trashExpanded }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.more_trashbin_count, trashNodes.size), color = WorkbenchColors.InkStrong, fontSize = 16.sp, fontWeight = FontWeight.Black)
            if (trashExpanded) {
                if (trashNodes.isEmpty()) Text(stringResource(R.string.more_trashbin_empty), color = WorkbenchColors.Muted, fontSize = 13.sp)
                trashNodes.forEach { node ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(node.title, color = WorkbenchColors.InkStrong, fontWeight = FontWeight.Black)
                        Text(areas.firstOrNull { it.id == node.areaId }?.let { displayAreaName(context, it) } ?: node.area, color = WorkbenchColors.Muted, fontSize = 13.sp)
                        Row { TextButton(onClick = { onRestoreNode(node) }) { Text(stringResource(R.string.common_restore)) }; TextButton(onClick = { deleteForeverNodeId = node.id }) { Text(stringResource(R.string.common_delete_forever), color = WorkbenchColors.Danger) } }
                    }
                }
            }
        }
    }
    trashNodes.firstOrNull { it.id == deleteForeverNodeId }?.let { node ->
        ConfirmDestructiveDialog(stringResource(R.string.more_delete_forever_confirm_title), stringResource(R.string.more_delete_forever_confirm_body, node.title), stringResource(R.string.common_delete_forever), { deleteForeverNodeId = null }, { deleteForeverNodeId = null; onDeleteForever(node) })
    }

}

@Composable
private fun LibraryAreaDetailScreen(
    area: AreaEntity,
    detail: LibraryAreaDetail,
    checkedFilter: LibraryCheckedFilter,
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    detail.title,
                    color = WorkbenchColors.InkStrong,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Black
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaPill(stringResource(R.string.common_nodes), detail.nodeCount.toString())
                    MetaPill(stringResource(R.string.library_checked_filter), detail.checkedCount.toString())
                }
                ToolbarRow {
                    WorkbenchButton(stringResource(R.string.library_back_to_areas), onBack)
                }
            }
            ToolbarRow {
                WorkbenchButton(stringResource(R.string.library_create_in_area_button), onNewNode, primary = true)
                WorkbenchButton(stringResource(R.string.common_edit), onRenameArea)
                WorkbenchButton(stringResource(R.string.common_delete), onDeleteArea, danger = true)
            }
        }

        ToolbarRow {
            WorkbenchButton(
                text = stringResource(R.string.library_filter_all),
                onClick = { onSetFilter(LibraryCheckedFilter.All) },
                primary = checkedFilter == LibraryCheckedFilter.All
            )
            WorkbenchButton(
                text = stringResource(R.string.library_filter_checked),
                onClick = { onSetFilter(LibraryCheckedFilter.Checked) },
                primary = checkedFilter == LibraryCheckedFilter.Checked
            )
        }

        if (detail.items.isEmpty()) {
            EmptyWorkbenchCard(
                title = stringResource(R.string.library_area_empty_title),
                body = stringResource(
                    if (checkedFilter == LibraryCheckedFilter.All) {
                        R.string.library_area_empty_body
                    } else {
                        R.string.library_checked_empty_body
                    }
                )
            )
        }

        detail.items.forEach { item ->
            val actions = libraryNodeCardActions()
            WorkbenchCard(accent = item.checked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (LibraryNodeCardAction.Check in actions) {
                        Checkbox(
                            checked = item.checked,
                            onCheckedChange = { onToggleChecked(item.node) }
                        )
                    }
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
                        ToolbarRow {
                            actions.forEach { action ->
                                when (action) {
                                    LibraryNodeCardAction.Read ->
                                        WorkbenchButton(stringResource(R.string.common_read), { onOpenNode(item.node) }, primary = true)
                                    LibraryNodeCardAction.Check -> Unit
                                    LibraryNodeCardAction.Edit ->
                                        WorkbenchButton(stringResource(R.string.common_edit), { onEditNode(item.node) })
                                    LibraryNodeCardAction.Move ->
                                        WorkbenchButton(stringResource(R.string.library_move_node_button), { onMoveNode(item.node) })
                                }
                            }
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

private fun LearningUiState.toLibraryScreenState(): LibraryScreenState =
    LibraryScreenState(
        areas = areas,
        nodes = nodes,
        trashNodes = trashNodes,
        dueQuizzes = dueQuizzes,
        selectedAreaId = selectedLibraryAreaId,
        checkedFilter = libraryCheckedFilter
    )
