# Android Phase 2A Node Command Transaction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route production Node create/edit through pure Content contracts and one Room transaction that atomically commits canonical/projection rows, an idempotency record, and a local versioned outbox item.

**Architecture:** Move existing Room schema ownership into `:core:database` without changing persisted names, introduce pure `:domain:content` and `:application:content`, and implement the command port in `:data:content-room`. Keep `:app` as a compatibility shell: only Node save delegates to the new port while every other query/write remains on the existing DAO path.

**Tech Stack:** Gradle 8.10.2, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Room 2.6.1, KSP, coroutines, `org.json`, JUnit 4, Robolectric, AndroidX Test Core, Room testing, PowerShell architecture gates.

---

## Source Layout

### New modules

- `android-app/core/database/` - Room database, all current persistence entities/DAO/converters, v1-v7 schema history, migration tests.
- `android-app/domain/content/` - pure Node model, edit policy, and Markdown quiz derivation.
- `android-app/application/content/` - pure save command, fingerprint, typed result, and command port.
- `android-app/data/content-room/` - mapper, codecs, projections, and transactional command adapter.

The composition root and Room adapter also depend directly on `:core:kernel`; their source constructs/reads `CommandId` and `EntityRevision`, and the current Kotlin JVM convention does not export producer `implementation` dependencies.

### Compatibility files

- `android-app/app/src/main/java/com/cslearningos/mobile/feature/library/data/LibraryRepository.kt` - delegates Node save only.
- `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt` - production composition accepts `LearningDatabase` and real Content port.
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningUiModels.kt` - pending Node save identity.
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/ExistingObjectEditorState.kt` - builds/reuses pending commands.
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt` - invalidates pending identity when editor input changes and clears it after success/cancel.

## Task 1: Establish The Phase 2A Module Graph

**Files:**
- Modify: `android-app/settings.gradle`
- Modify: `android-app/gradle/libs.versions.toml`
- Create: `android-app/core/database/build.gradle`
- Create: `android-app/domain/content/build.gradle`
- Create: `android-app/application/content/build.gradle`
- Create: `android-app/data/content-room/build.gradle`
- Modify: `android-app/app/build.gradle`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/architecture/StandaloneAndroidBoundaryTest.kt`

- [ ] **Step 1: Add failing project-graph assertions**

Add these project paths to `requiredProjects` in `StandaloneAndroidBoundaryTest`:

```kotlin
":core:database",
":domain:content",
":application:content",
":data:content-room"
```

Also assert each module directory contains a build file.

- [ ] **Step 2: Verify the boundary test fails**

Run from `android-app/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.architecture.StandaloneAndroidBoundaryTest --offline --console=plain
```

Expected: FAIL with `Missing :core:database`.

- [ ] **Step 3: Add dependency versions**

Extend `libs.versions.toml`:

```toml
room = "2.6.1"
robolectric = "4.13.2"
androidx-test-core = "1.6.1"

room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidx-test-core" }
```

- [ ] **Step 4: Include the four modules**

Append to `settings.gradle`:

```groovy
include ":core:database"
include ":domain:content"
include ":application:content"
include ":data:content-room"
```

- [ ] **Step 5: Add pure module builds**

`domain/content/build.gradle`:

```groovy
plugins { id "cslearningos.kotlin.library" }
dependencies {
    implementation project(":core:kernel")
    testImplementation libs.junit
}
```

`application/content/build.gradle`:

```groovy
plugins { id "cslearningos.kotlin.library" }
dependencies {
    implementation project(":core:kernel")
    implementation project(":domain:content")
    testImplementation libs.junit
}
```

- [ ] **Step 6: Add Android data module builds**

Both Android library modules use explicit plugins/configuration to avoid changing Phase 1 JVM conventions. `core/database/build.gradle`:

```groovy
plugins {
    id "com.android.library"
    id "org.jetbrains.kotlin.android"
    id "com.google.devtools.ksp"
}
android {
    namespace "com.cslearningos.mobile.database"
    compileSdk 35
    defaultConfig { minSdk 26 }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.includeAndroidResources = true }
}
dependencies {
    implementation libs.coroutines.core
    implementation libs.room.runtime
    implementation libs.room.ktx
    ksp libs.room.compiler
    testImplementation libs.junit
    testImplementation libs.room.testing
    testImplementation libs.robolectric
    testImplementation libs.androidx.test.core
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
```

`data/content-room/build.gradle`:

```groovy
plugins {
    id "com.android.library"
    id "org.jetbrains.kotlin.android"
}
android {
    namespace "com.cslearningos.mobile.content.room"
    compileSdk 35
    defaultConfig { minSdk 26 }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.includeAndroidResources = true }
}
dependencies {
    implementation project(":core:kernel")
    implementation project(":core:database")
    implementation project(":domain:content")
    implementation project(":application:content")
    implementation libs.coroutines.core
    implementation libs.room.ktx
    implementation libs.json
    testImplementation libs.junit
    testImplementation libs.room.testing
    testImplementation libs.robolectric
    testImplementation libs.androidx.test.core
    testImplementation libs.coroutines.test
}
```

- [ ] **Step 7: Add app dependencies and verify configuration**

Add the four project dependencies plus an explicit `:core:kernel` dependency to `app/build.gradle`, then run:

```powershell
.\gradlew.bat projects --offline --console=plain
```

Expected: all four new projects appear and configuration succeeds.

- [ ] **Step 8: Commit**

```powershell
git add android-app/settings.gradle android-app/gradle/libs.versions.toml android-app/core/database android-app/domain/content android-app/application/content android-app/data/content-room android-app/app/build.gradle android-app/app/src/test/java/com/cslearningos/mobile/architecture/StandaloneAndroidBoundaryTest.kt
git commit -m "build(android): add phase two content modules"
```

## Task 2: Move Room Ownership And Add The v7 Migration

**Files:**
- Move without package changes: `android-app/app/src/main/java/com/cslearningos/mobile/data/{AreaEntity,LearningEntities,LearningDao,LearningDatabase,RoomConverters}.kt`
- Move without package changes: `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/data/AssistantConversationEntity.kt`
- Move: `android-app/app/schemas/com.cslearningos.mobile.data.LearningDatabase/{1..6}.json`
- Create: `android-app/core/database/src/main/java/com/cslearningos/mobile/data/CommandPersistenceEntities.kt`
- Modify: moved `LearningDao.kt`
- Modify: moved `LearningDatabase.kt`
- Create: `android-app/core/database/src/test/java/com/cslearningos/mobile/data/LearningDatabaseMigrationTest.kt`
- Generate: `android-app/core/database/schemas/com.cslearningos.mobile.data.LearningDatabase/7.json`
- Modify: `android-app/app/build.gradle`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/data/LearningRepositoryPolicyTest.kt`

- [ ] **Step 1: Add the failing migration test**

Use `MigrationTestHelper` under Robolectric:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LearningDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LearningDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration6To7PreservesNodeAndCreatesEmptyOperationalTables() {
        helper.createDatabase("phase-2a-migration", 6).apply {
            execSQL("INSERT INTO areas(id,slug,name,`order`,created_at,updated_at,deleted_at) VALUES('systems','systems','Systems',1,1000,1000,NULL)")
            execSQL("INSERT INTO learning_nodes(id,title,markdown_body,created_at,updated_at,last_read_at,revision,sync_status,deleted_at,area,area_id,track,`order`,summary,visibility,is_starter,is_checked) VALUES('node-1','Paging','# Paging',1000,1000,NULL,4,'clean',NULL,'systems','systems','memory',1,'Paging','support',0,0)")
            close()
        }

        helper.runMigrationsAndValidate(
            "phase-2a-migration",
            7,
            true,
            LearningDatabase.Migration6To7
        ).use { db ->
            db.query("SELECT revision FROM learning_nodes WHERE id='node-1'").use {
                assertTrue(it.moveToFirst())
                assertEquals(4L, it.getLong(0))
            }
            db.query("SELECT COUNT(*) FROM processed_commands").use {
                assertTrue(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
            db.query("SELECT COUNT(*) FROM replication_outbox").use {
                assertTrue(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
        }
    }
}
```

- [ ] **Step 2: Verify the migration test cannot compile**

```powershell
.\gradlew.bat :core:database:testDebugUnitTest --tests com.cslearningos.mobile.data.LearningDatabaseMigrationTest --console=plain
```

Expected: FAIL because `LearningDatabase`, v7 entities, and migration are absent from the module.

- [ ] **Step 3: Move Room sources and schemas mechanically**

Keep every existing `package` declaration unchanged. Move schema versions 1-6 to `core/database/schemas/com.cslearningos.mobile.data.LearningDatabase/`. Move `AssistantConversationEntity.kt` into the core module while retaining package `com.cslearningos.mobile.feature.assistant.data`, so consumers do not change imports and `LearningDatabase` does not depend on `:app`.

- [ ] **Step 4: Add operational Room entities**

```kotlin
@Entity(tableName = "processed_commands")
data class ProcessedCommandEntity(
    @PrimaryKey @ColumnInfo(name = "command_id") val commandId: String,
    @ColumnInfo(name = "command_type") val commandType: String,
    @ColumnInfo(name = "request_fingerprint") val requestFingerprint: String,
    @ColumnInfo(name = "result_type") val resultType: String,
    @ColumnInfo(name = "result_payload_json") val resultPayloadJson: String,
    @ColumnInfo(name = "processed_at") val processedAt: Long
)

@Entity(
    tableName = "replication_outbox",
    indices = [
        Index(value = ["command_id"], unique = true),
        Index(value = ["state", "created_at"])
    ]
)
data class ReplicationOutboxEntity(
    @PrimaryKey @ColumnInfo(name = "change_id") val changeId: String,
    @ColumnInfo(name = "command_id") val commandId: String,
    @ColumnInfo(name = "aggregate_type") val aggregateType: String,
    @ColumnInfo(name = "aggregate_id") val aggregateId: String,
    val operation: String,
    @ColumnInfo(name = "base_revision") val baseRevision: Long?,
    @ColumnInfo(name = "new_revision") val newRevision: Long,
    @ColumnInfo(name = "domain_schema_version") val domainSchemaVersion: Int,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "payload_hash") val payloadHash: String,
    val state: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
```

- [ ] **Step 5: Add DAO operations**

```kotlin
@Query("SELECT * FROM processed_commands WHERE command_id = :commandId LIMIT 1")
suspend fun getProcessedCommand(commandId: String): ProcessedCommandEntity?

@Insert(onConflict = OnConflictStrategy.ABORT)
suspend fun insertProcessedCommand(command: ProcessedCommandEntity)

@Insert(onConflict = OnConflictStrategy.ABORT)
suspend fun insertOutbox(item: ReplicationOutboxEntity)

@Query("SELECT * FROM replication_outbox WHERE command_id = :commandId LIMIT 1")
suspend fun getOutboxForCommand(commandId: String): ReplicationOutboxEntity?
```

- [ ] **Step 6: Keep the existing Fake DAO compilable**

Extend the sole `FakeLearningDao` in `LearningRepositoryPolicyTest` with in-memory maps and exact overrides:

```kotlin
val processedCommands = linkedMapOf<String, ProcessedCommandEntity>()
val outbox = linkedMapOf<String, ReplicationOutboxEntity>()

override suspend fun getProcessedCommand(commandId: String): ProcessedCommandEntity? =
    processedCommands[commandId]
override suspend fun insertProcessedCommand(command: ProcessedCommandEntity) {
    check(command.commandId !in processedCommands)
    processedCommands[command.commandId] = command
}
override suspend fun insertOutbox(item: ReplicationOutboxEntity) {
    check(item.commandId !in outbox.values.map { it.commandId })
    outbox[item.changeId] = item
}
override suspend fun getOutboxForCommand(commandId: String): ReplicationOutboxEntity? =
    outbox.values.firstOrNull { it.commandId == commandId }
```

- [ ] **Step 7: Add `Migration6To7` and database entities**

Make `Migration6To7` internal, add both entity classes to `@Database`, set `version = 7`, and register the migration after `Migration5To6`. Execute this exact schema:

```sql
CREATE TABLE IF NOT EXISTS `processed_commands` (
  `command_id` TEXT NOT NULL,
  `command_type` TEXT NOT NULL,
  `request_fingerprint` TEXT NOT NULL,
  `result_type` TEXT NOT NULL,
  `result_payload_json` TEXT NOT NULL,
  `processed_at` INTEGER NOT NULL,
  PRIMARY KEY(`command_id`)
);
CREATE TABLE IF NOT EXISTS `replication_outbox` (
  `change_id` TEXT NOT NULL,
  `command_id` TEXT NOT NULL,
  `aggregate_type` TEXT NOT NULL,
  `aggregate_id` TEXT NOT NULL,
  `operation` TEXT NOT NULL,
  `base_revision` INTEGER,
  `new_revision` INTEGER NOT NULL,
  `domain_schema_version` INTEGER NOT NULL,
  `payload_json` TEXT NOT NULL,
  `payload_hash` TEXT NOT NULL,
  `state` TEXT NOT NULL,
  `created_at` INTEGER NOT NULL,
  PRIMARY KEY(`change_id`)
);
CREATE UNIQUE INDEX IF NOT EXISTS `index_replication_outbox_command_id`
  ON `replication_outbox` (`command_id`);
CREATE INDEX IF NOT EXISTS `index_replication_outbox_state_created_at`
  ON `replication_outbox` (`state`, `created_at`);
```

- [ ] **Step 8: Generate schema 7 and pass migration test**

```powershell
.\gradlew.bat :core:database:testDebugUnitTest --tests com.cslearningos.mobile.data.LearningDatabaseMigrationTest --console=plain
```

Expected: PASS and `7.json` is generated beside versions 1-6.

- [ ] **Step 9: Remove Room/KSP ownership from app and verify app compilation**

Remove the app-level Room dependencies, KSP plugin, and schema-location block only after all Room annotations have moved. Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin --offline --console=plain
```

Expected: PASS with existing package imports resolving through `:core:database`.

- [ ] **Step 10: Commit**

```powershell
git add android-app/core/database android-app/app/src/main android-app/app/schemas android-app/app/build.gradle
git commit -m "refactor(android): move room schema into database module"
```

## Task 3: Define The Pure Content Node Transition

**Files:**
- Create: `android-app/domain/content/src/main/kotlin/com/cslearningos/mobile/content/domain/ContentNode.kt`
- Create: `android-app/domain/content/src/main/kotlin/com/cslearningos/mobile/content/domain/NodeEditor.kt`
- Move/refactor: `android-app/app/src/main/java/com/cslearningos/mobile/domain/MarkdownQuizParser.kt`
- Move tests: `android-app/app/src/test/java/com/cslearningos/mobile/domain/MarkdownQuizParserTest.kt`
- Create: `android-app/domain/content/src/test/kotlin/com/cslearningos/mobile/content/domain/NodeEditorTest.kt`

- [ ] **Step 1: Write failing Node transition tests**

Cover creation, edit, stale revision, deleted target, title fallback, summary derivation, and preservation of non-editable content fields. Representative assertion:

```kotlin
@Test
fun editAdvancesRevisionAndPreservesStableFields() {
    val existing = contentNode(revision = 4L, track = "memory", lastUpdated = 1_000L)
    val result = NodeEditor.save(
        existing = existing,
        expectedRevision = EntityRevision(4L),
        nodeId = existing.id,
        area = ContentAreaRef("systems", "systems"),
        title = "Paging updated",
        markdownBody = "# Paging\n\nUpdated.",
        now = 2_000L
    )

    val updated = (result as NodeSaveDecision.Accepted).node
    assertEquals(EntityRevision(5L), updated.revision)
    assertEquals("memory", updated.track)
    assertEquals(1_000L, updated.createdAt)
}
```

- [ ] **Step 2: Verify pure tests fail to compile**

```powershell
.\gradlew.bat :domain:content:test --offline --console=plain
```

Expected: FAIL because the domain types do not exist.

- [ ] **Step 3: Implement domain types**

Define `NodeId`, `ContentAreaRef`, and this Room-free Node:

```kotlin
@JvmInline value class NodeId(val value: String) { init { require(value.isNotBlank()) } }
data class ContentAreaRef(val id: String, val slug: String)
data class ContentNode(
    val id: NodeId,
    val title: String,
    val markdownBody: String,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: EntityRevision,
    val deletedAt: Long?,
    val area: ContentAreaRef,
    val track: String,
    val order: Int,
    val summary: String,
    val visibility: String,
    val isStarter: Boolean,
    val isChecked: Boolean
)
```

Add decisions:

```kotlin
sealed interface NodeSaveDecision {
    data class Accepted(val node: ContentNode, val operation: ContentOperation) : NodeSaveDecision
    data class Rejected(val failure: ContentFailure) : NodeSaveDecision
}

sealed interface ContentFailure {
    data object Deleted : ContentFailure
    data class StaleRevision(val expected: EntityRevision, val actual: EntityRevision) : ContentFailure
    data class Validation(val code: String) : ContentFailure
}
```

`NodeEditor.save` creates revision 1 for a new Node and advances exactly once for an edit. The model contains content fields but no Room, `lastReadAt`, or `syncStatus`.

Implement the transition with this ordering:

```kotlin
object NodeEditor {
    fun save(
        existing: ContentNode?,
        expectedRevision: EntityRevision?,
        nodeId: NodeId,
        area: ContentAreaRef,
        title: String,
        markdownBody: String,
        now: Long
    ): NodeSaveDecision {
        if (title.isBlank() && markdownBody.isBlank()) {
            return NodeSaveDecision.Rejected(ContentFailure.Validation("content.empty"))
        }
        if (existing == null) {
            if (expectedRevision != null) {
                return NodeSaveDecision.Rejected(ContentFailure.Validation("create.has_revision"))
            }
            return NodeSaveDecision.Accepted(
                ContentNode(
                    id = nodeId,
                    title = title.ifBlank { "Untitled" },
                    markdownBody = markdownBody,
                    createdAt = now,
                    updatedAt = now,
                    revision = EntityRevision(1),
                    deletedAt = null,
                    area = area,
                    track = "general",
                    order = 1_000,
                    summary = summaryFrom(markdownBody),
                    visibility = "support",
                    isStarter = false,
                    isChecked = false
                ),
                ContentOperation.Create
            )
        }
        if (existing.id != nodeId) return NodeSaveDecision.Rejected(ContentFailure.Validation("node.id_mismatch"))
        if (existing.deletedAt != null) return NodeSaveDecision.Rejected(ContentFailure.Deleted)
        if (expectedRevision == null) {
            return NodeSaveDecision.Rejected(ContentFailure.Validation("update.missing_revision"))
        }
        if (expectedRevision != existing.revision) {
            return NodeSaveDecision.Rejected(
                ContentFailure.StaleRevision(expectedRevision, existing.revision)
            )
        }
        return NodeSaveDecision.Accepted(
            existing.copy(
                title = title.ifBlank { "Untitled" },
                markdownBody = markdownBody,
                updatedAt = now,
                revision = EntityRevision(existing.revision.value + 1),
                area = area
            ),
            ContentOperation.Update
        )
    }
}
```

`summaryFrom` returns the first nonblank, non-heading Markdown line trimmed, or an empty string.

- [ ] **Step 4: Move Markdown derivation into the domain module**

Move the existing parser behavior unchanged, rename packages to `com.cslearningos.mobile.content.domain`, and retain all seven existing parser tests. Replace app imports with the new package only after the pure module tests pass.

- [ ] **Step 5: Run domain and affected app tests**

```powershell
.\gradlew.bat :domain:content:test :app:testDebugUnitTest --tests "*MarkdownQuizParserTest" --offline --console=plain
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add android-app/domain/content android-app/app/src/main/java/com/cslearningos/mobile/domain/MarkdownQuizParser.kt android-app/app/src/test/java/com/cslearningos/mobile/domain/MarkdownQuizParserTest.kt android-app/app/src/main/java/com/cslearningos/mobile/feature/library/data/LibraryRepository.kt
git commit -m "feat(android): define content node domain"
```

## Task 4: Add The Idempotent Application Command Contract

**Files:**
- Create: `android-app/application/content/src/main/kotlin/com/cslearningos/mobile/content/application/SaveNodeCommand.kt`
- Create: `android-app/application/content/src/main/kotlin/com/cslearningos/mobile/content/application/CommandFingerprint.kt`
- Create: `android-app/application/content/src/main/kotlin/com/cslearningos/mobile/content/application/ContentCommandPort.kt`
- Create matching tests under `application/content/src/test/kotlin/...`

- [ ] **Step 1: Write failing fingerprint and contract tests**

Tests prove deterministic fingerprints, sensitivity to every request field, exclusion of `commandId`/`occurredAt`, and validation of create/update shapes:

```kotlin
@Test
fun retryIdentityExcludesCommandIdAndTimestamp() {
    val first = saveCommand(commandId = "one", occurredAt = 1_000L)
    val retry = saveCommand(commandId = "two", occurredAt = 9_000L)
    assertEquals(CommandFingerprint.of(first), CommandFingerprint.of(retry))
}

@Test
fun markdownChangeProducesDifferentFingerprint() {
    assertNotEquals(
        CommandFingerprint.of(saveCommand(markdown = "# A")),
        CommandFingerprint.of(saveCommand(markdown = "# B"))
    )
}
```

- [ ] **Step 2: Verify tests fail**

```powershell
.\gradlew.bat :application:content:test --offline --console=plain
```

Expected: FAIL because command types are absent.

- [ ] **Step 3: Implement command and result types**

```kotlin
enum class NodeSaveMode { Create, Update }

data class SaveNodeCommand(
    val commandId: CommandId,
    val nodeId: NodeId,
    val mode: NodeSaveMode,
    val expectedRevision: EntityRevision?,
    val areaId: String?,
    val title: String,
    val markdownBody: String,
    val occurredAt: Long
)

sealed interface ContentCommandResult {
    data class Success(val node: ContentNode) : ContentCommandResult
    data class Failure(val error: ContentCommandFailure) : ContentCommandResult
}

sealed interface ContentCommandFailure {
    data class Validation(val code: String) : ContentCommandFailure
    data class Missing(val target: String) : ContentCommandFailure
    data object Deleted : ContentCommandFailure
    data class StaleRevision(val expected: Long, val actual: Long) : ContentCommandFailure
    data object CommandReuseConflict : ContentCommandFailure
    data class Storage(val code: String) : ContentCommandFailure
}

fun interface ContentCommandPort {
    suspend fun saveNode(command: SaveNodeCommand): ContentCommandResult
}
```

Require create to omit `expectedRevision`; require update to supply it.

- [ ] **Step 4: Implement structured SHA-256 fingerprinting**

Use `ByteArrayOutputStream`/`DataOutputStream`: write a UTF-8 byte length before each nullable field and write expected revision as a signed long. Do not use delimiter concatenation or `writeUTF`. Include mode, Node ID, expected revision, Area ID, title, and exact Markdown. Exclude command ID and timestamp.

- [ ] **Step 5: Run tests and commit**

```powershell
.\gradlew.bat :application:content:test --offline --console=plain
git add android-app/application/content
git commit -m "feat(android): define idempotent content command"
```

## Task 5: Add Room Mapping And Canonical Codecs

**Files:**
- Create: `android-app/data/content-room/src/main/kotlin/com/cslearningos/mobile/content/room/NodeRoomMapper.kt`
- Create: `android-app/data/content-room/src/main/kotlin/com/cslearningos/mobile/content/room/ContentNodeCodec.kt`
- Create matching tests.

- [ ] **Step 1: Write failing mapper tests**

Prove `toDomain` excludes compatibility-only state and `toEntity` preserves reading trace while forcing legacy dirty status:

```kotlin
@Test
fun editedDomainNodePreservesReadingTraceAndWritesLegacyDirtyStatus() {
    val existing = nodeEntity(lastReadAt = 777L, syncStatus = SyncStatus.clean)
    val domain = NodeRoomMapper.toDomain(existing).copy(title = "Updated")

    val entity = NodeRoomMapper.toEntity(domain, existing)

    assertEquals(777L, entity.lastReadAt)
    assertEquals(SyncStatus.dirty, entity.syncStatus)
    assertEquals("Updated", entity.title)
}
```

Also test that encode/decode returns the same domain Node and that encoding/hash are deterministic.

- [ ] **Step 2: Verify tests fail**

```powershell
.\gradlew.bat :data:content-room:testDebugUnitTest --offline --console=plain
```

Expected: FAIL because mapper and codec do not exist.

- [ ] **Step 3: Implement mapper**

`toDomain` maps content fields and intentionally drops `lastReadAt`/`syncStatus`. Implement `toEntity` explicitly:

```kotlin
fun toEntity(node: ContentNode, existing: LearningNodeEntity?): LearningNodeEntity =
    LearningNodeEntity(
        id = node.id.value,
        title = node.title,
        markdownBody = node.markdownBody,
        createdAt = node.createdAt,
        updatedAt = node.updatedAt,
        lastReadAt = existing?.lastReadAt,
        revision = node.revision.value,
        syncStatus = SyncStatus.dirty,
        deletedAt = node.deletedAt,
        area = node.area.slug,
        areaId = node.area.id,
        track = node.track,
        order = node.order,
        summary = node.summary,
        visibility = node.visibility,
        isStarter = node.isStarter,
        isChecked = node.isChecked
    )
```

The inverse maps every content field and intentionally has no destination for compatibility-only values.

- [ ] **Step 4: Implement structured codec**

Use `JSONObject` with fixed insertion order and explicit getters. Payload schema 1 includes Node ID, content fields, revisions, timestamps, and deletion metadata; it excludes `syncStatus` and `lastReadAt`. Hash the exact UTF-8 bytes returned by the codec.

- [ ] **Step 5: Run tests and commit**

```powershell
.\gradlew.bat :data:content-room:testDebugUnitTest --offline --console=plain
git add android-app/data/content-room
git commit -m "feat(android): map content nodes to room"
```

## Task 6: Implement The Atomic Room Command Adapter

**Files:**
- Create: `android-app/data/content-room/src/main/kotlin/com/cslearningos/mobile/content/room/ContentProjectionWriter.kt`
- Create: `android-app/data/content-room/src/main/kotlin/com/cslearningos/mobile/content/room/RoomContentCommandAdapter.kt`
- Create: `android-app/data/content-room/src/test/kotlin/com/cslearningos/mobile/content/room/RoomContentCommandAdapterTest.kt`

- [ ] **Step 1: Write real Room failing tests**

Under Robolectric, build `LearningDatabase` in memory and seed an Area. Add tests for create, update, same-command replay, command reuse mismatch, stale revision, and injected projection failure. The success test asserts:

```kotlin
assertEquals(1, database.learningDao().getAllNodes().size)
assertEquals(1L, database.learningDao().getNode("node-1")!!.revision)
assertEquals(commandId, database.learningDao().getProcessedCommand(commandId)!!.commandId)
assertEquals(commandId, database.learningDao().getOutboxForCommand(commandId)!!.commandId)
```

The rollback test injects `ContentProjectionWriter { _, _, _ -> error("projection failed") }` and asserts Node, processed command, and outbox remain absent.

- [ ] **Step 2: Verify adapter tests fail**

```powershell
.\gradlew.bat :data:content-room:testDebugUnitTest --tests com.cslearningos.mobile.content.room.RoomContentCommandAdapterTest --console=plain
```

Expected: FAIL because the adapter is absent.

- [ ] **Step 3: Extract the projection writer**

Move Node FTS and Markdown-derived Quiz/Review logic from `LibraryRepository` into `RoomContentProjectionWriter`. Keep current stable row IDs, default review values, tombstoning of removed Markdown quizzes, and quiz FTS behavior unchanged. The writer receives `LearningDao`, saved `LearningNodeEntity`, and timestamp.

- [ ] **Step 4: Implement transaction and replay**

The replay branch inside `database.withTransaction` has this exact shape:

```kotlin
database.withTransaction {
    val fingerprint = CommandFingerprint.of(command)
    dao.getProcessedCommand(command.commandId.value)?.let { processed ->
        return@withTransaction if (processed.requestFingerprint == fingerprint) {
            ContentCommandResult.Success(ContentNodeCodec.decode(processed.resultPayloadJson))
        } else {
            ContentCommandResult.Failure(ContentCommandFailure.CommandReuseConflict)
        }
    }
}
```

For the first execution, continue in the same lambda by loading target/Area, invoking `NodeEditor`, writing the Node and projections, inserting the outbox, and inserting the processed result last. Domain rejections return before the first write. Do not catch implementation exceptions inside the lambda.

Wrap the complete `database.withTransaction` call in an outer `try/catch`. Rethrow coroutine cancellation; map other implementation exceptions to `ContentCommandFailure.Storage` only after Room has rolled the transaction back. Map missing Area/Node, deleted Node, stale revision, and validation to their exact application failures. Never include title, Markdown, payload, or API data in error codes.

- [ ] **Step 5: Insert deterministic local change metadata**

Create one outbox row with aggregate type `content.node`, operation `create`/`update`, base/new revisions, schema version 1, exact encoded Node payload/hash, state `pending`, and command timestamp. Use an injected `changeIdFactory` defaulting to UUID so tests remain deterministic.

- [ ] **Step 6: Run transaction and migration tests**

```powershell
.\gradlew.bat :core:database:testDebugUnitTest :data:content-room:testDebugUnitTest --console=plain
```

Expected: PASS, including the rollback and replay cases.

- [ ] **Step 7: Commit**

```powershell
git add android-app/data/content-room android-app/core/database
git commit -m "feat(android): commit node commands atomically"
```

## Task 7: Route The Editor Through The New Port

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/library/data/LibraryRepository.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningUiModels.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/ExistingObjectEditorState.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/ui/AssistantAppBridge.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/backup/ui/RestoreStateReducer.kt`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/data/LearningRepositoryPolicyTest.kt`
- Test: focused app tests for editor retry identity and navigation.

- [ ] **Step 1: Write failing pending-command state tests**

Add tests proving unchanged retry reuses ID/Node ID, editing title/body/Area clears pending identity, successful save/cancel clears it, and new/edit editor entry starts clean:

```kotlin
@Test
fun unchangedFailedNodeSaveReusesPendingIdentity() {
    val first = stateWithDraft().withPendingNodeSave()
    val retry = first.withPendingNodeSave()
    assertEquals(first.pendingNodeSave, retry.pendingNodeSave)
}

@Test
fun changedDraftInvalidatesPendingIdentity() {
    val pending = stateWithDraft().withPendingNodeSave()
    assertNull(pending.withEditorBody("changed").pendingNodeSave)
}
```

- [ ] **Step 2: Verify focused app tests fail**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.cslearningos.mobile.ui.*Editor*" --offline --console=plain
```

Expected: FAIL because pending command state does not exist.

- [ ] **Step 3: Add pending save state**

Add:

```kotlin
data class PendingNodeSave(
    val commandId: String,
    val nodeId: String,
    val fingerprint: String
)
```

to the editor state surface, plus `pendingNodeSave: PendingNodeSave? = null` on `LearningUiState`. `withPendingNodeSave` creates stable UUIDs and uses `CommandFingerprint`; create allocates a Node ID, update reuses `editorNodeId`.

- [ ] **Step 4: Invalidate identity on intent changes**

Update title/body/Area setters to clear `pendingNodeSave`. Clear it on editor entry, assistant draft replacement, restore reset, successful save, cancel, and trash navigation. Do not clear it in `withObjectSaveRejected`.

- [ ] **Step 5: Delegate compatibility save**

Change `LibraryRepository` to receive `ContentCommandPort`. Add `saveNode(command: SaveNodeCommand)` as the editor path. Preserve the existing parameter overload for `SaveNodeUseCase`, but make it allocate a one-shot command/Node ID and immediately delegate; for an update whose caller omits `expectedRevision`, load the current row revision before constructing the command to preserve that legacy overload's behavior. It must contain no DAO mutation logic. Remove the old save mutation/projection implementation and translate the command result:

```kotlin
when (val result = contentCommands.saveNode(command)) {
    is ContentCommandResult.Success ->
        NodeRoomMapper.toEntity(result.node, dao.getNode(result.node.id.value))
    is ContentCommandResult.Failure -> throw result.error.toCompatibilityException()
}
```

Change `LearningRepository.saveNodeFromEditor` to require the `PendingNodeSave`, build the exact command below, and call the explicit command overload:

```kotlin
SaveNodeCommand(
    commandId = CommandId(pending.commandId),
    nodeId = NodeId(pending.nodeId),
    mode = if (state.editorNodeId == null) NodeSaveMode.Create else NodeSaveMode.Update,
    expectedRevision = state.editorExpectedRevision?.let(::EntityRevision),
    areaId = state.editorAreaId,
    title = state.editorTitle,
    markdownBody = state.editorBody,
    occurredAt = now
)
```

The returned entity is for compatibility/UI only; the adapter already committed it.

- [ ] **Step 6: Compose production dependencies**

Change `LearningViewModel` to retain one database instance:

```kotlin
private val database = LearningDatabase.create(application)
private val repository = LearningRepository(
    database = database,
    contentCommands = RoomContentCommandAdapter(database)
)
```

Keep a test constructor accepting `LearningDao` plus an explicit/fake `ContentCommandPort` for legacy unit tests that do not create Room.

- [ ] **Step 7: Retire duplicated fake save-policy tests**

Remove the old Fake-DAO Node save tests for missing Node, stale revision, deleted Node, and explicit missing Area from `LearningRepositoryPolicyTest`; Task 3 and Task 6 now cover those cases through pure policy and real Room transactions. Keep all non-save repository tests. Construct their repository with an explicit `ContentCommandPort` that fails if unexpectedly called:

```kotlin
private val unexpectedContentCommands = ContentCommandPort {
    error("Content command is outside this legacy repository test")
}
```

- [ ] **Step 8: Run editor, repository, Assistant, and backup compatibility tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.cslearningos.mobile.ui.*" --tests "com.cslearningos.mobile.data.*" --tests "com.cslearningos.mobile.feature.assistant.*" --tests "com.cslearningos.mobile.feature.backup.*" --offline --console=plain
```

Expected: PASS with unchanged visible save/navigation behavior.

- [ ] **Step 9: Commit**

```powershell
git add android-app/app/src/main android-app/app/src/test
git commit -m "refactor(android): route node saves through command port"
```

## Task 8: Enforce Boundaries And Verify Phase 2A

**Files:**
- Modify: `scripts/verify-android-architecture.ps1`
- Modify: `scripts/test-verify-android-architecture.ps1`
- Modify: `android-app/docs/architecture.md`
- Modify: `docs/state-machine.md`

- [ ] **Step 1: Add failing architecture regression fixtures**

Require the four Phase 2A modules and exact dependency allowlist:

```text
core/database -> none of app/domain/application/data projects
domain/content -> core/kernel
application/content -> core/kernel, domain/content
data/content-room -> core/kernel, core/database, domain/content, application/content
app -> core/kernel plus all active public/composition modules
```

Add negative fixtures for `domain/content` importing Room, `application/content` importing old app data, `core/database` depending on `:app`, and `data/content-room` depending on UI.

- [ ] **Step 2: Verify old architecture script misses a new violation**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\test-verify-android-architecture.ps1
```

Expected: FAIL on the first Phase 2A fixture not yet enforced.

- [ ] **Step 3: Extend deterministic module/source checks**

Update the required project list, dependency allowlist, pure-source roots, and forbidden namespace sets. Ensure every offending file/reference is printed. Retain the constrained local Gradle input allowlist and Phase 1 checks.

- [ ] **Step 4: Document the active transaction path**

Update Android architecture and state-machine docs with the actual Phase 2A module graph, v7 migration, command replay semantics, local outbox status, and explicit statement that network replication and causal envelopes remain unimplemented.

- [ ] **Step 5: Run fresh full verification**

From `android-app/`:

```powershell
.\gradlew.bat :core:kernel:test :domain:assistant:test :domain:content:test :application:content:test :feature:assistant:impl:test :adapter:model-openai:test :core:database:testDebugUnitTest :data:content-room:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain --rerun-tasks
```

From repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\test-verify-android-architecture.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-android-architecture.ps1
```

Expected: all tests execute with zero failures, Debug APK builds, and both architecture commands exit 0.

- [ ] **Step 6: Inspect schema, graph, and worktree**

```powershell
.\gradlew.bat projects --offline --console=plain
git status --short
git diff --check
```

Expected: schema versions 1-7 are tracked under `core/database`, all four modules appear, and only known pre-existing untracked paths remain.

- [ ] **Step 7: Commit documentation and gates**

```powershell
git add scripts/verify-android-architecture.ps1 scripts/test-verify-android-architecture.ps1 android-app/docs/architecture.md docs/state-machine.md android-app/core/database/schemas
git commit -m "docs(android): enforce phase two transaction boundary"
```
