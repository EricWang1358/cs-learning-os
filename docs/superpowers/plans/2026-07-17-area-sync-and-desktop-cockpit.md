# Area Sync And Desktop Cockpit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make node and quiz area changes sync-safe on Android, then reshape the desktop Sync/Health/Graph views into a clearer cockpit-style experience.

**Architecture:** Android keeps the existing repository boundaries, but area-changing library operations must emit the same kind of durable content outbox records as explicit node and quiz saves. The desktop web app keeps its current single-app shell, but the Sync/Health/Graph modes get a dedicated cockpit layout layer so those views stop inheriting the cramped knowledge-reading layout.

**Tech Stack:** Kotlin, Room, Robolectric, React 19, TypeScript, Vite, CSS.

---

## File Map

- `android-app/app/src/main/java/com/cslearningos/mobile/feature/library/data/LibraryRepository.kt`: node move logic and linked quiz updates.
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/review/data/QuizOutboxCodec.kt`: canonical quiz payload serialization for outbox writes.
- `android-app/core/database/src/main/java/com/cslearningos/mobile/data/LearningDao.kt`: transactional outbox helpers.
- `android-app/app/src/test/java/com/cslearningos/mobile/data/LearningRepositoryPolicyTest.kt`: area move regression coverage.
- `android-app/app/src/test/java/com/cslearningos/mobile/feature/sync/SyncRepositoryTest.kt`: push behavior after area-changing local edits.
- `app/src/App.tsx`: desktop mode routing and shell composition.
- `app/src/components/SyncPanel.tsx`: Sync cockpit content structure.
- `app/src/components/GraphNavigator.tsx`: graph cockpit content structure.
- `app/src/components/HealthActionPanels.tsx`: health cockpit panel content.
- `app/src/App.css`: mode-specific cockpit layout and responsive polish.

### Task 1: Lock The Android Regression With Failing Tests

**Files:**
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/data/LearningRepositoryPolicyTest.kt`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/feature/sync/SyncRepositoryTest.kt`

- [ ] **Step 1: Write a failing repository regression test for node area moves**

```kotlin
@Test
fun movingNodeToAreaUpdatesLinkedQuizAndCreatesSyncableOutboxChanges() = runTest {
    val dao = FakeLearningDao()
    val repository = repository(dao)
    seedSystemsNodeWithLinkedQuiz(dao)
    dao.areas["algorithms"] = area("algorithms", "Algorithms")

    repository.moveNodeToArea(nodeId = "node-1", targetAreaId = "algorithms", now = 5_000L)

    assertEquals("algorithms", dao.nodes.getValue("node-1").areaId)
    assertEquals("algorithms", dao.quizzes.getValue("quiz-1").area)
    assertEquals(2, dao.outbox.values.count { it.state == "pending" })
}
```

- [ ] **Step 2: Run the single Android test and verify it fails for the right reason**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.data.LearningRepositoryPolicyTest.movingNodeToAreaUpdatesLinkedQuizAndCreatesSyncableOutboxChanges`

Expected: FAIL because `moveNodeToArea()` currently mutates rows without creating any `content.node` / `content.quiz` outbox change.

- [ ] **Step 3: Write a failing sync preparation test for moved quiz content**

```kotlin
@Test
fun pushLocalChangesIncludesMovedQuizAreaFromGeneratedOutboxPayload() = runTest {
    val dao = FakeDao()
    dao.quizzes["quiz-1"] = dirtyQuiz(id = "quiz-1", area = "algorithms", revision = 2, baseRevision = 1)
    dao.outbox["quiz-change-1"] = pendingQuizOutbox("quiz-change-1", "quiz-1", area = "algorithms", revision = 2)

    val repository = SyncRepository(dao.proxy(), FakeTransport(), store) { 100L }
    repository.pushLocalChanges()

    assertEquals("algorithms", JSONObject(FakeTransport.lastQuizPush!!.single().toString()).getString("area"))
}
```

- [ ] **Step 4: Run the focused sync test and verify it fails if the outbox payload is stale or missing**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.feature.sync.SyncRepositoryTest.pushLocalChangesIncludesMovedQuizAreaFromGeneratedOutboxPayload`

Expected: FAIL until area-changing operations generate valid pending quiz outbox payloads.

### Task 2: Implement The Minimal Android Outbox Fix

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/library/data/LibraryRepository.kt`
- Modify: `android-app/core/database/src/main/java/com/cslearningos/mobile/data/LearningDao.kt`

- [ ] **Step 1: Add transactional helper(s) for updated content plus outbox writes**

```kotlin
@Transaction
suspend fun saveMovedNodeAndQuizzes(
    node: LearningNodeEntity,
    nodeFts: NodeFtsEntity?,
    nodeOutbox: ReplicationOutboxEntity,
    quizzes: List<QuizItemEntity>,
    quizFts: List<QuizFtsEntity>,
    quizOutbox: List<ReplicationOutboxEntity>
) {
    upsertNode(node)
    deleteNodeFts(node.id)
    if (nodeFts != null) upsertNodeFts(nodeFts)
    insertOutbox(nodeOutbox)
    quizzes.forEach { upsertQuiz(it) }
    quizzes.forEach { deleteQuizFts(it.id) }
    quizFts.forEach { upsertQuizFts(it) }
    quizOutbox.forEach { insertOutbox(it) }
}
```

- [ ] **Step 2: Update `moveNodeToArea()` to emit canonical node and quiz outbox payloads**

```kotlin
val nodePayload = ContentNodeCodec.encode(NodeRoomMapper.toDomain(updated))
val nodeOutbox = ReplicationOutboxEntity(
    changeId = UUID.randomUUID().toString(),
    commandId = UUID.randomUUID().toString(),
    aggregateType = "content.node",
    aggregateId = updated.id,
    operation = "update",
    baseRevision = node.baseRevision.takeIf { it > 0 } ?: node.revision,
    newRevision = updated.revision,
    domainSchemaVersion = ContentNodeCodec.SchemaVersion,
    payloadJson = nodePayload,
    payloadHash = ContentNodeCodec.sha256Hex(nodePayload),
    state = "pending",
    createdAt = now
)
```

Use the existing updated quiz entities to create matching `content.quiz` outbox records via `QuizOutboxCodec.encode(updatedQuiz)`. Keep the implementation narrow: only `moveNodeToArea()` gets this new durable sync behavior in this pass.

- [ ] **Step 3: Re-run the focused Android regression tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.data.LearningRepositoryPolicyTest.movingNodeToAreaUpdatesLinkedQuizAndCreatesSyncableOutboxChanges --tests com.cslearningos.mobile.feature.sync.SyncRepositoryTest.pushLocalChangesIncludesMovedQuizAreaFromGeneratedOutboxPayload`

Expected: `BUILD SUCCESSFUL`.

### Task 3: Reshape The Desktop Cockpit Views

**Files:**
- Modify: `app/src/App.tsx`
- Modify: `app/src/components/SyncPanel.tsx`
- Modify: `app/src/components/GraphNavigator.tsx`
- Modify: `app/src/components/HealthActionPanels.tsx`
- Modify: `app/src/App.css`

- [ ] **Step 1: Write mode-specific layout expectations into the component structure**

```tsx
{viewMode === 'sync' && (
  <section className="cockpit-mode-shell sync-cockpit">
    <div className="cockpit-hero">...</div>
    <div className="cockpit-columns">...</div>
  </section>
)}
```

- [ ] **Step 2: Move Sync away from the inherited narrow detail rail**

Use a top summary band for endpoint / protocol / paired devices, then a two-column body with pairing actions on the left and trusted-device policy on the right. Keep copy buttons and device permission toggles exactly as-is functionally.

- [ ] **Step 3: Give Graph and Health the same cockpit treatment**

Preserve existing data sources, but stop rendering them as if they were note-reader detail panes. Graph gets a centered hero + action bar + canvas region; Health gets a metrics hero + action grid + issue panels with stronger hierarchy.

- [ ] **Step 4: Re-run desktop smoke/build checks**

Run: `npm --prefix app run build`

Expected: Vite build succeeds with no TypeScript errors.

### Task 4: Final Verification

**Files:**
- Modify only implementation files touched above.

- [ ] **Step 1: Run the Android unit suite**

Run: `.\gradlew.bat :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run the desktop build and smoke checks**

Run: `npm --prefix app run build`

Expected: successful production build.

- [ ] **Step 3: Check the diff for whitespace issues**

Run: `git diff --check`

Expected: no whitespace errors.
