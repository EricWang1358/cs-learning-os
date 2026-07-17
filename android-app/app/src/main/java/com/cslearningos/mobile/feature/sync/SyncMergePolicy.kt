package com.cslearningos.mobile.feature.sync

import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.SyncStatus

/** Typed merge outcomes for one remote change against the local row. */
enum class MergeDecision {
    /** Upsert the remote record (create or overwrite a clean local row). */
    APPLY,

    /** Upsert the remote record AND park the dirty local version as a conflict draft. */
    APPLY_WITH_CONFLICT,

    /** Remote tombstone over a clean local row: soft-delete locally. */
    SOFT_DELETE,

    /** Remote tombstone over a dirty local row: keep the local text, mark conflicted. */
    KEEP_LOCAL,

    /** Nothing to do (idempotent replay or unknown local state). */
    SKIP
}

fun decideNodeMerge(
    local: LearningNodeEntity?,
    remoteRevision: Long,
    tombstone: Boolean
): MergeDecision = decideMerge(
    localExists = local != null,
    localDirty = local?.syncStatus == SyncStatus.dirty || local?.syncStatus == SyncStatus.conflicted,
    localBaseRevision = local?.baseRevision ?: 0L,
    remoteRevision = remoteRevision,
    tombstone = tombstone
)

fun decideQuizMerge(
    local: QuizItemEntity?,
    remoteRevision: Long,
    tombstone: Boolean
): MergeDecision = decideMerge(
    localExists = local != null,
    localDirty = local?.syncStatus == SyncStatus.dirty || local?.syncStatus == SyncStatus.conflicted,
    localBaseRevision = local?.baseRevision ?: 0L,
    remoteRevision = remoteRevision,
    tombstone = tombstone
)

private fun decideMerge(
    localExists: Boolean,
    localDirty: Boolean,
    localBaseRevision: Long,
    remoteRevision: Long,
    tombstone: Boolean
): MergeDecision = when {
    tombstone && !localExists -> MergeDecision.SKIP
    tombstone && localDirty -> MergeDecision.KEEP_LOCAL
    tombstone -> MergeDecision.SOFT_DELETE
    !localExists -> MergeDecision.APPLY
    localBaseRevision >= remoteRevision -> MergeDecision.SKIP
    localDirty -> MergeDecision.APPLY_WITH_CONFLICT
    else -> MergeDecision.APPLY
}
