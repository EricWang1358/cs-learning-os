# Quiz Upstream Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Synchronize manually created and edited Android Quiz records to the paired desktop with idempotent, version-checked writes and no silent local-data loss.

**Architecture:** A manual Quiz save produces a stable `content.quiz` outbox item in the same Room transaction as the Quiz and FTS projection. `SyncRepository` sends only the earliest pending item per Quiz ID, then conditionally acknowledges the local row from an authoritative desktop revision receipt. The desktop preserves Markdown frontmatter it does not own while updating the synced fields and uses its normal SQLite, FTS, and change-envelope write path.

**Tech Stack:** Kotlin, Room, `org.json`, OkHttp, FastAPI, Pydantic, SQLite, pytest, Gradle/Robolectric.

**Working-tree rule:** Do not commit in this plan. The current `main` worktree is intentionally dirty with user-owned work.

---

## File Map

- `android-app/app/src/main/java/com/cslearningos/mobile/feature/review/data/ReviewRepository.kt`: construct manual Quiz revisions and delegate the canonical/outbox write.
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/review/data/QuizOutboxCodec.kt`: versioned JSON codec and Android-to-desktop Markdown projection for a Quiz outbox payload.
- `android-app/core/database/src/main/java/com/cslearningos/mobile/data/LearningDao.kt`: atomic Quiz/outbox save, pending-query, and conditional acknowledgement operations.
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/sync/SyncRepository.kt`: enqueue `content.quiz` payloads and consume push receipts.
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/sync/SyncTransport.kt`: expose `POST /push/quizzes`.
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/sync/SyncModels.kt`: include `uploadedQuizzes` in the push report.
- `backend/api_models.py`: validate the Quiz push payload.
- `backend/sync_router.py`: authenticated `POST /api/sync/v1/push/quizzes` route.
- `backend/sync_service.py`: idempotent, revision-checked Quiz write service and retained-frontmatter helper.
- `backend/test_sync_push.py`: HTTP and service contract tests.
- `android-app/app/src/test/java/com/cslearningos/mobile/feature/review/ReviewRepositoryTest.kt`: Quiz save/outbox transaction behavior.
- `android-app/app/src/test/java/com/cslearningos/mobile/feature/sync/SyncRepositoryTest.kt`: push acknowledgement and stale/newer-edit behavior.

### Task 1: Versioned Quiz Outbox Write

**Files:**
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/review/data/QuizOutboxCodec.kt`
- Modify: `android-app/core/database/src/main/java/com/cslearningos/mobile/data/LearningDao.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/review/data/ReviewRepository.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/feature/review/ReviewRepositoryTest.kt`

- [ ] **Step 1: Write a failing save/outbox test**

```kotlin
@Test
fun saveManualQuizWritesTheSameRevisionToQuizAndContentOutbox() = runTest {
    val dao = FakeReviewDao()
    val saved = ReviewRepository(dao).saveManualQuiz(
        nodeId = null, areaId = "algorithms", prompt = "What is a TLB?",
        answer = "A translation cache.", explanation = "It caches translations.", now = 10L
    )

    assertEquals(1L, saved.revision)
    val outbox = dao.outbox.single()
    assertEquals("content.quiz", outbox.aggregateType)
    assertEquals(saved.id, outbox.aggregateId)
    assertEquals(null, outbox.baseRevision)
    assertEquals(saved.revision, outbox.newRevision)
    assertEquals(saved.id, QuizOutboxCodec.decode(outbox.payloadJson).id)
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.feature.review.ReviewRepositoryTest.saveManualQuizWritesTheSameRevisionToQuizAndContentOutbox`

Expected: failure because `QuizOutboxCodec` and the Quiz outbox write do not exist.

- [ ] **Step 3: Add the Quiz payload codec and atomic DAO operation**

```kotlin
data class QuizOutboxPayload(
    val id: String, val prompt: String, val answer: String, val explanation: String,
    val area: String, val visibility: String, val revision: Long
)

object QuizOutboxCodec {
    const val SchemaVersion = 1
    fun encode(quiz: QuizItemEntity): String = JSONObject()
        .put("schemaVersion", SchemaVersion).put("id", quiz.id)
        .put("prompt", quiz.prompt).put("answer", quiz.answer)
        .put("explanation", quiz.explanation).put("area", quiz.area)
        .put("visibility", quiz.visibility)
        .put("revision", quiz.revision).toString()
    fun decode(payload: String): QuizOutboxPayload {
        val json = JSONObject(payload)
        require(json.getInt("schemaVersion") == SchemaVersion)
        return QuizOutboxPayload(
            id = json.getString("id"), prompt = json.getString("prompt"),
            answer = json.getString("answer"), explanation = json.getString("explanation"),
            area = json.getString("area"), visibility = json.getString("visibility"),
            revision = json.getLong("revision")
        )
    }
    fun desktopBody(payload: QuizOutboxPayload): String =
        "## Prompt\n\n${payload.prompt}\n\n## Answer\n\n${payload.answer}" +
            if (payload.explanation.isBlank()) "" else "\n\n## Explanation\n\n${payload.explanation}"
}

@Transaction
suspend fun saveManualQuizWithOutbox(
    quiz: QuizItemEntity,
    initialState: ReviewStateEntity?,
    fts: QuizFtsEntity,
    outbox: ReplicationOutboxEntity
) {
    upsertQuiz(quiz)
    if (initialState != null) upsertReviewState(initialState)
    deleteQuizFts(quiz.id)
    upsertQuizFts(fts)
    insertOutbox(outbox)
}
```

`ReviewRepository.saveManualQuiz` creates a UUID `changeId`, encodes the saved Quiz, and calls `saveManualQuizWithOutbox` instead of separate DAO writes. It sets `operation` to `create` or `update`, `baseRevision` to the prior revision or `null`, and `state` to `pending`.

- [ ] **Step 4: Run the focused Android test and verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.feature.review.ReviewRepositoryTest.saveManualQuizWritesTheSameRevisionToQuizAndContentOutbox`

Expected: `BUILD SUCCESSFUL`.

### Task 2: Android Quiz Push And Conditional Receipt Acknowledgement

**Files:**
- Modify: `android-app/core/database/src/main/java/com/cslearningos/mobile/data/LearningDao.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/sync/SyncTransport.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/sync/SyncModels.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/sync/SyncRepository.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/feature/sync/SyncRepositoryTest.kt`

- [ ] **Step 1: Write failing push acknowledgement tests**

```kotlin
@Test
fun acceptedQuizReceiptAcknowledgesOnlyTheMatchingCurrentRevision() = runTest {
    dao.quizzes["q1"] = dirtyQuiz(id = "q1", revision = 2, baseRevision = 1)
    dao.outbox["quiz-c1"] = pendingQuizOutbox("quiz-c1", "q1", baseRevision = 1, revision = 2)
    transport.quizReceipts = listOf(SyncReceipt("q1", "accepted", null, revision = 2))

    val report = SyncRepository(dao.proxy(), transport, store).pushLocalChanges()

    assertEquals(1, report.uploadedQuizzes)
    assertEquals(SyncStatus.clean, dao.quizzes.getValue("q1").syncStatus)
    assertTrue(dao.outbox.isEmpty())
}

@Test
fun staleQuizReceiptLeavesOutboxAndLaterEditDirty() = runTest {
    dao.quizzes["q1"] = dirtyQuiz(id = "q1", revision = 3, baseRevision = 1)
    dao.outbox["quiz-c1"] = pendingQuizOutbox("quiz-c1", "q1", baseRevision = 1, revision = 2)
    transport.quizReceipts = listOf(SyncReceipt("q1", "rejected", "stale_revision", revision = 2))

    SyncRepository(dao.proxy(), transport, store).pushLocalChanges()

    assertEquals(SyncStatus.dirty, dao.quizzes.getValue("q1").syncStatus)
    assertTrue("quiz-c1" in dao.outbox)
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.feature.sync.SyncRepositoryTest.acceptedQuizReceiptAcknowledgesOnlyTheMatchingCurrentRevision --tests com.cslearningos.mobile.feature.sync.SyncRepositoryTest.staleQuizReceiptLeavesOutboxAndLaterEditDirty`

Expected: compile failure because `uploadedQuizzes`, Quiz outbox DAO operations, and `pushQuizzes` are absent.

- [ ] **Step 3: Implement the minimal transport/repository contract**

```kotlin
interface SyncTransport {
    suspend fun pushQuizzes(items: List<JSONObject>): List<SyncReceipt>
}

@Query("SELECT * FROM replication_outbox WHERE aggregate_type = 'content.quiz' AND state = 'pending' ORDER BY created_at, change_id LIMIT :limit")
suspend fun getPendingQuizOutbox(limit: Int): List<ReplicationOutboxEntity>

@Transaction
suspend fun acknowledgeQuizContentPush(changeId: String, quizId: String, localRevision: Long, serverRevision: Long) {
    markQuizContentSynced(quizId, localRevision, serverRevision)
    deleteOutboxItem(changeId)
}
```

In `SyncRepository`, decode each `QuizOutboxCodec` payload, group pending items by `aggregateId`, retain the first item in each group, and post DTOs shaped as:

```json
{
  "changeId": "quiz-c1",
  "id": "q1",
  "area": "algorithms",
  "body": "## Prompt\n\nWhat is a TLB?\n\n## Answer\n\nA translation cache.",
  "visibility": "practice",
  "baseRevision": 1,
  "revision": 2,
  "tombstone": false
}
```

Only `accepted` receipts with a non-null revision call `acknowledgeQuizContentPush`; all other receipts leave the outbox untouched and increase `rejected`.

- [ ] **Step 4: Run the focused Android tests and verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.feature.sync.SyncRepositoryTest.acceptedQuizReceiptAcknowledgesOnlyTheMatchingCurrentRevision --tests com.cslearningos.mobile.feature.sync.SyncRepositoryTest.staleQuizReceiptLeavesOutboxAndLaterEditDirty`

Expected: `BUILD SUCCESSFUL`.

### Task 3: Desktop Quiz Push Contract

**Files:**
- Modify: `backend/api_models.py`
- Modify: `backend/sync_router.py`
- Modify: `backend/sync_service.py`
- Test: `backend/test_sync_push.py`

- [ ] **Step 1: Write failing desktop contract tests**

```python
def test_push_quiz_updates_matching_revision_once_and_rejects_stale_change(tmp_path: Path) -> None:
    # Seed q1 at revision 1 with tags and a custom frontmatter field.
    first = sync_service.push_quizzes(conn, content_root, "phone-a", [quiz_item])
    replay = sync_service.push_quizzes(conn, content_root, "phone-a", [quiz_item])
    stale = sync_service.push_quizzes(conn, content_root, "phone-a", [{**quiz_item, "changeId": "q2", "revision": 2}])

    assert first == [{"id": "q1", "status": "accepted", "revision": 2}]
    assert replay == first
    assert stale == [{"id": "q1", "status": "rejected", "reason": "stale_revision", "revision": 2}]
    assert 'tags: ["memory"]' in source_path.read_text(encoding="utf-8")

def test_push_quizzes_endpoint_requires_push_scope_and_uses_content_root(tmp_path: Path) -> None:
    response = client.post("/api/sync/v1/push/quizzes", json={"items": [quiz_item]}, headers=headers)
    assert response.status_code == 200
    assert response.json()["receipts"] == [{"id": "q1", "status": "accepted", "revision": 2}]
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./.venv/Scripts/python.exe -m pytest backend/test_sync_push.py::test_push_quiz_updates_matching_revision_once_and_rejects_stale_change backend/test_sync_push.py::test_push_quizzes_endpoint_requires_push_scope_and_uses_content_root -q`

Expected: failure because the Quiz push model, route, and service do not exist.

- [ ] **Step 3: Implement a retained-frontmatter Quiz write**

```python
class SyncPushQuizItem(BaseModel):
    changeId: str = Field(min_length=1, max_length=64)
    id: str = Field(min_length=1, max_length=64)
    title: Optional[str] = Field(default=None, max_length=300)
    area: str = Field(min_length=1, max_length=80)
    difficulty: str = Field(default="", max_length=40)
    summary: Optional[str] = None
    body: str = Field(min_length=1)
    visibility: str = Field(pattern="^(practice|support|draft|archive)$")
    baseRevision: Optional[int] = Field(default=None, ge=0)
    revision: int = Field(ge=1)
    tombstone: bool = False
```

Add `POST /api/sync/v1/push/quizzes`, gated by `sync:push`, and implement `push_quizzes(conn, content_root, device_id, items)`. Reuse `sync_push_receipts` and `_content_receipt` from the node flow. Android updates only the Markdown body, area, and visibility. It omits title, summary, and difficulty because it cannot represent them; existing desktop values remain intact and a new mobile Quiz derives its title from the Prompt and defaults its difficulty to `medium`. The service retains every other frontmatter line, then updates `quizzes`, `quiz_fts`, `graph_cache`, revision, and `sync_changes` in one transaction. A malformed existing Markdown source returns `invalid_source`; tombstones return `tombstone_unsupported`.

- [ ] **Step 4: Run desktop contract tests and verify they pass**

Run: `./.venv/Scripts/python.exe -m pytest backend/test_sync_push.py -q`

Expected: all push contract tests pass.

### Task 4: Full Regression Verification

**Files:**
- Modify: `docs/superpowers/specs/2026-07-17-quiz-upstream-sync-design.md` only if implementation changes an approved behavior.

- [ ] **Step 1: Run backend regression suite**

Run: `./.venv/Scripts/python.exe -m pytest backend -q`

Expected: zero failures; record pre-existing warnings separately from test failures.

- [ ] **Step 2: Run Android tests and produce a Debug APK**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --rerun-tasks`

Expected: `BUILD SUCCESSFUL`; record only existing deprecation warnings.

- [ ] **Step 3: Check the working tree for whitespace errors and unintended files**

Run: `git diff --check && git status --short`

Expected: no whitespace errors. Do not revert unrelated user-owned modifications or create a commit.
