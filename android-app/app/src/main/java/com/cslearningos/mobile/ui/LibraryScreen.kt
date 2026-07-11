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
    val selectedArea = screenState.areas.firstOrNull { it.id == screenState.selectedAreaId }
    val root = buildLibraryRootModel(screenState.areas, screenState.nodes, screenState.dueQuizzes, context)
    val detail = selectedArea?.let {
        buildLibraryAreaDetail(it, screenState.nodes, screenState.dueQuizzes, screenState.checkedFilter, context)
    }

    var showCreateAreaDialog by rememberSaveable { mutableStateOf(screenState.areas.isEmpty()) }
    var createAreaDraft by rememberSaveable { mutableStateOf("") }
    var renameAreaId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameAreaDraft by rememberSaveable { mutableStateOf("") }
    var moveNodeId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (detail == null) {
            LibraryRootScreen(
                folders = root.folders,
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
                areas = screenState.areas
            )
        } else {
            LibraryAreaDetailScreen(
                area = selectedArea,
                detail = detail,
                checkedFilter = screenState.checkedFilter,
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
    onOpenArea: (String) -> Unit,
    onCreateArea: () -> Unit,
    onRenameArea: (AreaEntity) -> Unit,
    onDeleteArea: (AreaEntity) -> Unit,
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaPill(stringResource(R.string.common_nodes), folder.nodeCount.toString(), Modifier.width(LibraryLayoutTokens.FolderMetricWidth).height(LibraryLayoutTokens.FolderMetricHeight))
                    MetaPill(stringResource(R.string.library_checked_filter), folder.checkedCount.toString(), Modifier.width(LibraryLayoutTokens.FolderMetricWidth).height(LibraryLayoutTokens.FolderMetricHeight))
                    if (folder.dueCount > 0) {
                        MetaPill(stringResource(R.string.common_due), folder.dueCount.toString(), Modifier.width(LibraryLayoutTokens.FolderMetricWidth).height(LibraryLayoutTokens.FolderMetricHeight))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Row(
                    modifier = Modifier.weight(LibraryLayoutTokens.FolderActionGroupWidthFraction),
                    horizontalArrangement = Arrangement.spacedBy(LibraryLayoutTokens.FolderActionGap)
                ) {
                    WorkbenchButton(stringResource(R.string.common_open), { onOpenArea(folder.areaId) }, Modifier.weight(1f), primary = true)
                    WorkbenchButton(stringResource(R.string.common_edit), { onRenameArea(area) }, Modifier.weight(1f))
                    WorkbenchButton(stringResource(R.string.common_delete), { onDeleteArea(area) }, Modifier.weight(1f), danger = true)
                }
            }
        }
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
                        ToolbarRow {
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

private fun LearningUiState.toLibraryScreenState(): LibraryScreenState =
    LibraryScreenState(
        areas = areas,
        nodes = nodes,
        dueQuizzes = dueQuizzes,
        selectedAreaId = selectedLibraryAreaId,
        checkedFilter = libraryCheckedFilter
    )
