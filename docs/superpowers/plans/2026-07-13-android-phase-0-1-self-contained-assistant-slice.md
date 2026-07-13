# Android Phase 0-1 Self-Contained Assistant Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `android-app/` self-contained and prove the target dependency direction with one real Assistant vertical slice while preserving current Room, backup, UI, and assistant behavior.

**Architecture:** Keep the existing `:app` as a compatibility shell while introducing pure Kotlin kernel/domain/feature-contract modules and a replaceable OpenAI-compatible adapter. Move real endpoint/SSE behavior and assistant run lifecycle contracts behind module boundaries, then enforce those boundaries in the architecture harness.

**Tech Stack:** Gradle 8.10.2, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Kotlin coroutines/Flow, Room 2.6.1, JUnit 4, `org.json`, PowerShell CI harness.

---

## Scope

This plan implements Phase 0 and Phase 1 only. It does not split Room entities, remove `LearningRepository`, complete the four-aggregate Assistant Runtime, or implement replication. Those are later child projects under the approved umbrella spec.

## File Structure

### New build and module files

- `android-app/gradle/libs.versions.toml` - shared dependency versions.
- `android-app/build-logic/settings.gradle.kts` - included-build repositories.
- `android-app/build-logic/build.gradle.kts` - convention plugin build.
- `android-app/build-logic/src/main/kotlin/cslearningos.kotlin.library.gradle.kts` - Java 17 Kotlin/JVM convention.
- `android-app/core/kernel/build.gradle.kts` - pure shared-kernel module.
- `android-app/core/kernel/src/main/kotlin/com/cslearningos/mobile/core/kernel/KernelTypes.kt` - stable IDs, revisions, results.
- `android-app/domain/assistant/build.gradle.kts` - pure assistant-domain module.
- `android-app/domain/assistant/src/main/kotlin/com/cslearningos/mobile/assistant/domain/AssistantModelGateway.kt` - provider-neutral model contract.
- `android-app/domain/assistant/src/main/kotlin/com/cslearningos/mobile/assistant/domain/AssistantRunMachine.kt` - run reducer and effects.
- `android-app/feature/assistant/api/build.gradle.kts` - assistant entry public API.
- `android-app/feature/assistant/api/src/main/kotlin/com/cslearningos/mobile/assistant/api/AssistantEntry.kt` - entry request contract.
- `android-app/feature/assistant/impl/build.gradle.kts` - assistant entry policy implementation.
- `android-app/feature/assistant/impl/src/main/kotlin/com/cslearningos/mobile/assistant/impl/AssistantEntryPolicy.kt` - fresh/preserve policy.
- `android-app/adapter/model-openai/build.gradle.kts` - OpenAI-compatible model adapter.
- `android-app/adapter/model-openai/src/main/kotlin/com/cslearningos/mobile/assistant/openai/OpenAiModelGateway.kt` - HTTP/SSE implementation.
- `android-app/adapter/model-openai/src/main/kotlin/com/cslearningos/mobile/assistant/openai/OpenAiSseParser.kt` - provider wire parsing.

### New documentation and tests

- `android-app/docs/architecture.md` - standalone Android architecture entry point.
- `android-app/docs/data-recovery.md` - Android-owned recovery contract.
- `android-app/app/src/test/java/com/cslearningos/mobile/architecture/StandaloneAndroidBoundaryTest.kt` - no-parent-dependency regression test.
- Unit tests beside each new JVM module under `src/test/kotlin/...`.

### Existing files modified

- `android-app/settings.gradle` - included build, version catalog, and real module includes.
- `android-app/app/build.gradle` - repository-local starter content and module dependencies.
- `android-app/app/src/test/java/com/cslearningos/mobile/docs/ProjectDocsTest.kt` - Android-root-only documentation checks.
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/data/KnowledgeAssistantService.kt` - compatibility facade over `ModelGateway`.
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/ui/AssistantAppBridge.kt` - typed entry contract.
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt` - app-level entry binding.
- Relevant assistant tests - typed API and adapter imports.
- `scripts/verify-android-architecture.ps1` - module/import/source-boundary enforcement.
- `docs/state-machine.md` - approved Android assistant target state ownership and migration marker.

## Task 1: Make Starter Content And Documentation Self-Contained

**Files:**
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/architecture/StandaloneAndroidBoundaryTest.kt`
- Create: `android-app/docs/architecture.md`
- Create: `android-app/docs/data-recovery.md`
- Create mechanically: `android-app/starter-content/**` from tracked `content-demo/**`
- Modify: `android-app/app/build.gradle`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/docs/ProjectDocsTest.kt`
- Modify: `android-app/README.md`

- [ ] **Step 1: Write the failing standalone boundary test**

```kotlin
package com.cslearningos.mobile.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandaloneAndroidBoundaryTest {
    @Test
    fun buildInputsStayInsideAndroidRoot() {
        val root = androidRoot()
        val appBuild = root.resolve("app/build.gradle").readText(Charsets.UTF_8)
        val settings = root.resolve("settings.gradle").readText(Charsets.UTF_8)

        assertFalse(appBuild.contains("../content-demo"))
        assertTrue(appBuild.contains("\$rootDir/starter-content"))
        assertFalse(settings.contains("../app"))
        assertFalse(settings.contains("../backend"))
        assertTrue(root.resolve("starter-content/index.md").isFile)
        assertTrue(root.resolve("docs/architecture.md").isFile)
        assertTrue(root.resolve("docs/data-recovery.md").isFile)
    }

    private fun androidRoot(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            if (current.resolve("settings.gradle").isFile && current.resolve("app").isDirectory) return current
            current = current.parentFile
        }
        error("Could not locate standalone Android root from ${System.getProperty("user.dir")}")
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run from `android-app/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.architecture.StandaloneAndroidBoundaryTest
```

Expected: FAIL because `app/build.gradle` still uses `../content-demo` and Android-owned docs/starter content do not exist.

- [ ] **Step 3: Copy the public demo package into the Android root**

Run from repository root:

```powershell
Copy-Item -Path content-demo -Destination android-app\starter-content -Recurse
```

Expected: 23 tracked Markdown files under `android-app/starter-content/`, byte-identical to the public demo source.

- [ ] **Step 4: Point Gradle at the repository-local package**

Change the source in `android-app/app/build.gradle`:

```groovy
tasks.register("syncStarterContentAssets", Sync) {
    from("$rootDir/starter-content") {
        include "nodes/**/*.md"
        include "quizzes/**/*.md"
        exclude ".trash/**"
        exclude "quizzes/cs-fundamentals/x86-rax-trace-leaq-jump.md"
        exclude "quizzes/cs-fundamentals/gdb-disassemble-stepi-stack-examine.md"
    }
    into starterAssetsDir
}
```

- [ ] **Step 5: Add Android-owned architecture and recovery docs**

Create `android-app/docs/architecture.md` with:

```markdown
# Android Architecture

The Android product is local-first and independently buildable. Room-backed local data is the application read source of truth. UI sends typed actions to screen state holders; application commands validate domain rules and commit canonical rows, projections, and future replication outbox records atomically.

The migration target and dependency rules are defined in the repository architecture specification. During Phase 1 the existing `:app` remains a compatibility shell while pure domain contracts and replaceable adapters move into Gradle modules.

No required build input may resolve outside this Android repository root.
```

Create `android-app/docs/data-recovery.md` with:

```markdown
# Android Data Recovery

Backup schema v1 remains readable throughout the modular migration. Restore is an explicit, previewed operation; malformed or unsupported input must leave Room unchanged. Incremental device synchronization is not a backup replacement and is not implemented in Phase 1.

Before any future destructive migration, export a backup through the in-app Backup screen and verify that the exported file can be selected for restore.
```

- [ ] **Step 6: Make `ProjectDocsTest` locate Android root only**

Replace its desktop-root reads with:

```kotlin
val root = androidRoot()
val readme = root.resolve("README.md").readText(Charsets.UTF_8)
val architecture = root.resolve("docs/architecture.md").readText(Charsets.UTF_8)
val dataRecovery = root.resolve("docs/data-recovery.md").readText(Charsets.UTF_8)

listOf(readme, architecture, dataRecovery).forEach { text ->
    assertFalse(text.contains('\uFFFD'))
}
assertTrue(readme.contains("Network permission policy"))
assertTrue(readme.contains("Advanced commands"))
assertTrue(architecture.contains("independently buildable"))
assertTrue(dataRecovery.contains("Backup schema v1"))
```

Rename `repoRoot()` to `androidRoot()` and detect `settings.gradle`, `app/`, and `README.md` without checking desktop `app/`.

- [ ] **Step 7: Update Android README links**

Replace the parent-doc link with:

```markdown
See [docs/architecture.md](docs/architecture.md) and [docs/data-recovery.md](docs/data-recovery.md).
```

- [ ] **Step 8: Run focused documentation and boundary tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.architecture.StandaloneAndroidBoundaryTest --tests com.cslearningos.mobile.docs.ProjectDocsTest
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add android-app/app/build.gradle android-app/app/src/test/java/com/cslearningos/mobile/architecture/StandaloneAndroidBoundaryTest.kt android-app/app/src/test/java/com/cslearningos/mobile/docs/ProjectDocsTest.kt android-app/docs android-app/README.md android-app/starter-content
git commit -m "build(android): make starter inputs self contained"
```

## Task 2: Add Convention Build Logic And The Minimum Real Module Graph

**Execution note (2026-07-13):** The repository uses Groovy Gradle scripts, and the local dependency cache did not contain the Kotlin DSL compiler chain. The implemented convention build therefore uses `groovy-gradle-plugin`, `settings.gradle`, `build.gradle`, and `KotlinLibraryConventionPlugin.groovy`. The module graph and dependency rules are unchanged, and both online and subsequent `--offline` `projects` checks pass.

**Files:**
- Create: `android-app/gradle/libs.versions.toml`
- Create: `android-app/build-logic/settings.gradle.kts`
- Create: `android-app/build-logic/build.gradle.kts`
- Create: `android-app/build-logic/src/main/kotlin/cslearningos.kotlin.library.gradle.kts`
- Create: build files for `core/kernel`, `domain/assistant`, `feature/assistant/api`, `feature/assistant/impl`, `adapter/model-openai`
- Modify: `android-app/settings.gradle`
- Modify: `android-app/app/build.gradle`

- [ ] **Step 1: Add a failing module-graph assertion to the boundary test**

```kotlin
val requiredProjects = listOf(
    ":core:kernel",
    ":domain:assistant",
    ":feature:assistant:api",
    ":feature:assistant:impl",
    ":adapter:model-openai"
)
requiredProjects.forEach { projectPath ->
    assertTrue("Missing $projectPath", settings.contains("include \"$projectPath\""))
}
assertTrue(root.resolve("build-logic/src/main/kotlin/cslearningos.kotlin.library.gradle.kts").isFile)
```

- [ ] **Step 2: Run the boundary test and verify the missing projects fail**

Run the Task 1 focused command. Expected: FAIL with `Missing :core:kernel`.

- [ ] **Step 3: Add the version catalog**

```toml
[versions]
kotlin = "2.0.21"
coroutines = "1.9.0"
json = "20240303"
junit = "4.13.2"

[libraries]
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
json = { module = "org.json:json", version.ref = "json" }
junit = { module = "junit:junit", version.ref = "junit" }
```

- [ ] **Step 4: Add the included convention build**

`build-logic/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "android-build-logic"
```

`build-logic/build.gradle.kts`:

```kotlin
plugins { `kotlin-dsl` }

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
}
```

`build-logic/src/main/kotlin/cslearningos.kotlin.library.gradle.kts`:

```kotlin
plugins { kotlin("jvm") }

kotlin { jvmToolchain(17) }

tasks.withType<Test>().configureEach { useJUnit() }
```

- [ ] **Step 5: Include the real projects**

Add near the top of `settings.gradle`:

```groovy
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Keep the existing repository declarations and add:

```groovy
include ":core:kernel"
include ":domain:assistant"
include ":feature:assistant:api"
include ":feature:assistant:impl"
include ":adapter:model-openai"
```

- [ ] **Step 6: Add exact module build files**

`core/kernel/build.gradle.kts`:

```kotlin
plugins { id("cslearningos.kotlin.library") }
dependencies { testImplementation(libs.junit) }
```

`domain/assistant/build.gradle.kts`:

```kotlin
plugins { id("cslearningos.kotlin.library") }
dependencies {
    implementation(project(":core:kernel"))
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
```

`feature/assistant/api/build.gradle.kts`:

```kotlin
plugins { id("cslearningos.kotlin.library") }
dependencies { testImplementation(libs.junit) }
```

`feature/assistant/impl/build.gradle.kts`:

```kotlin
plugins { id("cslearningos.kotlin.library") }
dependencies {
    implementation(project(":feature:assistant:api"))
    testImplementation(libs.junit)
}
```

`adapter/model-openai/build.gradle.kts`:

```kotlin
plugins { id("cslearningos.kotlin.library") }
dependencies {
    implementation(project(":domain:assistant"))
    implementation(libs.coroutines.core)
    implementation(libs.json)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
```

- [ ] **Step 7: Add project dependencies to the compatibility app**

```groovy
implementation project(":domain:assistant")
implementation project(":feature:assistant:api")
implementation project(":feature:assistant:impl")
implementation project(":adapter:model-openai")
```

- [ ] **Step 8: Verify the graph configures**

```powershell
.\gradlew.bat projects
```

Expected: all five new project paths appear and configuration succeeds.

- [ ] **Step 9: Commit**

```powershell
git add android-app/settings.gradle android-app/app/build.gradle android-app/gradle/libs.versions.toml android-app/build-logic android-app/core android-app/domain android-app/feature android-app/adapter
git commit -m "build(android): establish modular dependency graph"
```

## Task 3: Introduce The Assistant Feature API Boundary

**Files:**
- Create: `android-app/feature/assistant/api/src/main/kotlin/com/cslearningos/mobile/assistant/api/AssistantEntry.kt`
- Create: `android-app/feature/assistant/impl/src/main/kotlin/com/cslearningos/mobile/assistant/impl/AssistantEntryPolicy.kt`
- Test: `android-app/feature/assistant/impl/src/test/kotlin/com/cslearningos/mobile/assistant/impl/AssistantEntryPolicyTest.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/ui/AssistantAppBridge.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/ui/LearningViewModelAssistantNavigationTest.kt`

- [ ] **Step 1: Write the failing entry-policy test**

```kotlin
package com.cslearningos.mobile.assistant.impl

import com.cslearningos.mobile.assistant.api.AssistantConversationPolicy
import com.cslearningos.mobile.assistant.api.AssistantEntryRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantEntryPolicyTest {
    @Test fun freshEntryResetsConversation() =
        assertTrue(AssistantEntryPolicy.shouldReset(AssistantEntryRequest(AssistantConversationPolicy.Fresh)))

    @Test fun preserveEntryKeepsConversation() =
        assertFalse(AssistantEntryPolicy.shouldReset(AssistantEntryRequest(AssistantConversationPolicy.Preserve)))
}
```

- [ ] **Step 2: Run the new test and verify it fails to compile**

```powershell
.\gradlew.bat :feature:assistant:impl:test
```

Expected: FAIL because the API and policy types do not exist.

- [ ] **Step 3: Implement the public API and policy**

```kotlin
package com.cslearningos.mobile.assistant.api

enum class AssistantConversationPolicy { Fresh, Preserve }

data class AssistantEntryRequest(
    val conversationPolicy: AssistantConversationPolicy,
    val target: AssistantTargetRef? = null
)

data class AssistantTargetRef(
    val type: String,
    val id: String?,
    val revision: Long?
)
```

```kotlin
package com.cslearningos.mobile.assistant.impl

import com.cslearningos.mobile.assistant.api.AssistantConversationPolicy
import com.cslearningos.mobile.assistant.api.AssistantEntryRequest

object AssistantEntryPolicy {
    fun shouldReset(request: AssistantEntryRequest): Boolean =
        request.conversationPolicy == AssistantConversationPolicy.Fresh
}
```

- [ ] **Step 4: Route existing bridge entry through the typed contract**

Replace the two bridge callbacks with:

```kotlin
private val onShowAssistant: (AssistantEntryRequest) -> Unit
```

Use `Fresh` for existing Node/Quiz/Capture improve entry and `Preserve` for improving an already-open editor draft. In `LearningViewModel`, bind:

```kotlin
onShowAssistant = { request ->
    if (AssistantEntryPolicy.shouldReset(request)) showAssistantFresh()
    else showAssistantPreservingConversation()
}
```

Update the navigation test harness to pass `onShowAssistant = {}`.

- [ ] **Step 5: Run API, implementation, and navigation tests**

```powershell
.\gradlew.bat :feature:assistant:impl:test :app:testDebugUnitTest --tests com.cslearningos.mobile.ui.LearningViewModelAssistantNavigationTest
```

Expected: PASS and current fresh/preserve behavior remains unchanged.

- [ ] **Step 6: Commit**

```powershell
git add android-app/feature/assistant android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/ui/AssistantAppBridge.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt android-app/app/src/test/java/com/cslearningos/mobile/ui/LearningViewModelAssistantNavigationTest.kt
git commit -m "refactor(android): type assistant entry boundary"
```

## Task 4: Add Pure Assistant Run And Model Contracts

**Files:**
- Create: `android-app/core/kernel/src/main/kotlin/com/cslearningos/mobile/core/kernel/KernelTypes.kt`
- Create: `android-app/domain/assistant/src/main/kotlin/com/cslearningos/mobile/assistant/domain/AssistantModelGateway.kt`
- Create: `android-app/domain/assistant/src/main/kotlin/com/cslearningos/mobile/assistant/domain/AssistantRunMachine.kt`
- Test: matching module test files.

- [ ] **Step 1: Write failing kernel and reducer tests**

Test that blank IDs and negative revisions fail, then test this transition path:

```kotlin
val runId = AssistantRunId("run-1")
val started = AssistantRunMachine.reduce(AssistantRunState.Idle, AssistantRunEvent.Start(runId))
assertEquals(AssistantRunState.BuildingContext(runId), started.state)
assertEquals(listOf(AssistantRunEffect.BuildContext(runId)), started.effects)

val streaming = AssistantRunMachine.reduce(started.state, AssistantRunEvent.ContextReady(runId))
assertEquals(AssistantRunState.Streaming(runId), streaming.state)
assertEquals(listOf(AssistantRunEffect.StartModel(runId)), streaming.effects)

val stale = AssistantRunMachine.reduce(streaming.state, AssistantRunEvent.ModelCompleted(AssistantRunId("old")))
assertEquals(streaming.state, stale.state)
assertTrue(stale.effects.isEmpty())
```

- [ ] **Step 2: Run module tests and verify missing types fail**

```powershell
.\gradlew.bat :core:kernel:test :domain:assistant:test
```

Expected: FAIL at test compilation.

- [ ] **Step 3: Implement stable kernel types**

```kotlin
package com.cslearningos.mobile.core.kernel

@JvmInline value class CommandId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class EntityRevision(val value: Long) { init { require(value >= 0L) } }

sealed interface DomainResult<out T> {
    data class Success<T>(val value: T) : DomainResult<T>
    data class Failure(val error: DomainFailure) : DomainResult<Nothing>
}

sealed interface DomainFailure {
    data class Validation(val code: String) : DomainFailure
    data class Conflict(val code: String) : DomainFailure
    data class Missing(val code: String) : DomainFailure
}
```

- [ ] **Step 4: Implement the provider-neutral model contract**

```kotlin
package com.cslearningos.mobile.assistant.domain

import kotlinx.coroutines.flow.Flow

@JvmInline
value class AssistantRunId(val value: String) {
    init { require(value.isNotBlank()) }
}

enum class ModelRole(val wireValue: String) {
    System("system"),
    User("user"),
    Assistant("assistant")
}

data class ModelMessage(
    val role: ModelRole,
    val content: String
)

data class ModelRequest(
    val runId: AssistantRunId,
    val messages: List<ModelMessage>
) {
    init { require(messages.isNotEmpty()) }
}

data class ModelCapabilities(
    val streaming: Boolean,
    val structuredOutput: Boolean,
    val toolCalls: Boolean,
    val contextWindowTokens: Int?
)

sealed interface ModelFailure {
    data class Authentication(val statusCode: Int) : ModelFailure
    data class RateLimited(val retryAfterSeconds: Long?) : ModelFailure
    data class Http(val statusCode: Int, val safeMessage: String) : ModelFailure
    data class Protocol(val safeMessage: String) : ModelFailure
    data class Transport(val safeMessage: String) : ModelFailure
}

sealed interface ModelEvent {
    data class Token(val runId: AssistantRunId, val value: String) : ModelEvent
    data class Completed(val runId: AssistantRunId) : ModelEvent
    data class Failed(val runId: AssistantRunId, val failure: ModelFailure) : ModelEvent
}

interface ModelGateway {
    suspend fun capabilities(): ModelCapabilities
    fun stream(request: ModelRequest): Flow<ModelEvent>
}
```

No type in this file may import Android, HTTP, JSON, app UI, resources, or provider-specific names.

- [ ] **Step 5: Implement the pure run reducer**

```kotlin
package com.cslearningos.mobile.assistant.domain

sealed interface AssistantRunState {
    data object Idle : AssistantRunState
    data class BuildingContext(val runId: AssistantRunId) : AssistantRunState
    data class Streaming(val runId: AssistantRunId) : AssistantRunState
    data class Parsing(val runId: AssistantRunId) : AssistantRunState
    data class Completed(val runId: AssistantRunId) : AssistantRunState
    data class Failed(val runId: AssistantRunId, val code: String) : AssistantRunState
    data class Cancelled(val runId: AssistantRunId) : AssistantRunState
    data class Superseded(val runId: AssistantRunId) : AssistantRunState
}

sealed interface AssistantRunEvent {
    data class Start(val runId: AssistantRunId) : AssistantRunEvent
    data class ContextReady(val runId: AssistantRunId) : AssistantRunEvent
    data class ModelCompleted(val runId: AssistantRunId) : AssistantRunEvent
    data class ParseCompleted(val runId: AssistantRunId) : AssistantRunEvent
    data class Fail(val runId: AssistantRunId, val code: String) : AssistantRunEvent
    data class Cancel(val runId: AssistantRunId) : AssistantRunEvent
    data class Supersede(val runId: AssistantRunId) : AssistantRunEvent
}

sealed interface AssistantRunEffect {
    data class BuildContext(val runId: AssistantRunId) : AssistantRunEffect
    data class StartModel(val runId: AssistantRunId) : AssistantRunEffect
    data class ParseResult(val runId: AssistantRunId) : AssistantRunEffect
}

data class AssistantRunTransition(
    val state: AssistantRunState,
    val effects: List<AssistantRunEffect> = emptyList()
)

object AssistantRunMachine {
    fun reduce(state: AssistantRunState, event: AssistantRunEvent): AssistantRunTransition {
        if (event !is AssistantRunEvent.Start && state.runIdOrNull() != event.runId()) {
            return AssistantRunTransition(state)
        }
        return when (event) {
            is AssistantRunEvent.Start -> when (state) {
                AssistantRunState.Idle,
                is AssistantRunState.Completed,
                is AssistantRunState.Failed,
                is AssistantRunState.Cancelled,
                is AssistantRunState.Superseded -> AssistantRunTransition(
                    AssistantRunState.BuildingContext(event.runId),
                    listOf(AssistantRunEffect.BuildContext(event.runId))
                )
                else -> AssistantRunTransition(state)
            }
            is AssistantRunEvent.ContextReady -> AssistantRunTransition(
                AssistantRunState.Streaming(event.runId),
                listOf(AssistantRunEffect.StartModel(event.runId))
            )
            is AssistantRunEvent.ModelCompleted -> AssistantRunTransition(
                AssistantRunState.Parsing(event.runId),
                listOf(AssistantRunEffect.ParseResult(event.runId))
            )
            is AssistantRunEvent.ParseCompleted -> AssistantRunTransition(AssistantRunState.Completed(event.runId))
            is AssistantRunEvent.Fail -> AssistantRunTransition(AssistantRunState.Failed(event.runId, event.code))
            is AssistantRunEvent.Cancel -> AssistantRunTransition(AssistantRunState.Cancelled(event.runId))
            is AssistantRunEvent.Supersede -> AssistantRunTransition(AssistantRunState.Superseded(event.runId))
        }
    }
}

private fun AssistantRunState.runIdOrNull(): AssistantRunId? = when (this) {
    AssistantRunState.Idle -> null
    is AssistantRunState.BuildingContext -> runId
    is AssistantRunState.Streaming -> runId
    is AssistantRunState.Parsing -> runId
    is AssistantRunState.Completed -> runId
    is AssistantRunState.Failed -> runId
    is AssistantRunState.Cancelled -> runId
    is AssistantRunState.Superseded -> runId
}

private fun AssistantRunEvent.runId(): AssistantRunId = when (this) {
    is AssistantRunEvent.Start -> runId
    is AssistantRunEvent.ContextReady -> runId
    is AssistantRunEvent.ModelCompleted -> runId
    is AssistantRunEvent.ParseCompleted -> runId
    is AssistantRunEvent.Fail -> runId
    is AssistantRunEvent.Cancel -> runId
    is AssistantRunEvent.Supersede -> runId
}
```

Only matching run IDs may advance active state. Cancel and supersede are terminal. `Start` is accepted only from `Idle` or a terminal state.

- [ ] **Step 6: Run pure module tests**

```powershell
.\gradlew.bat :core:kernel:test :domain:assistant:test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add android-app/core/kernel android-app/domain/assistant
git commit -m "feat(android): define assistant run domain"
```

## Task 5: Move OpenAI-Compatible HTTP And SSE Behind `ModelGateway`

**Files:**
- Create: `android-app/adapter/model-openai/src/main/kotlin/com/cslearningos/mobile/assistant/openai/OpenAiModelGateway.kt`
- Create: `android-app/adapter/model-openai/src/main/kotlin/com/cslearningos/mobile/assistant/openai/OpenAiSseParser.kt`
- Test: adapter parser/gateway tests.
- Modify: existing `KnowledgeAssistantService.kt` into compatibility facade.
- Modify: `AssistantStreamParserTest.kt` and relevant coordinator/session tests only as imports require.

- [ ] **Step 1: Write failing adapter parser tests**

Cover `[DONE]`, token delta, absent/null token, control lines, structured error, and non-SSE JSON fallback. Use representative wire lines already covered by `AssistantStreamParserTest`.

- [ ] **Step 2: Run adapter tests and verify missing parser fails**

```powershell
.\gradlew.bat :adapter:model-openai:test
```

Expected: FAIL at test compilation.

- [ ] **Step 3: Implement endpoint normalization and SSE parsing**

`OpenAiSseParser` returns provider-neutral parser results:

```kotlin
sealed interface OpenAiSseResult {
    data class Token(val value: String) : OpenAiSseResult
    data class Error(val message: String) : OpenAiSseResult
    data object Control : OpenAiSseResult
    data object Done : OpenAiSseResult
    data object Ignored : OpenAiSseResult
}
```

Normalize base URL by trimming trailing `/`; append `/chat/completions` unless the user already supplied that path.

- [ ] **Step 4: Implement `OpenAiModelGateway`**

The constructor receives base URL, API key, model, connect/read timeouts, and temperature. `stream()` uses `flow { withContext(Dispatchers.IO) { ... } }`, disconnects on coroutine cancellation, emits token events, emits one completed event, and maps HTTP/SSE/JSON failures to typed `ModelFailure`. It must not log or include the API key in failure text.

- [ ] **Step 5: Convert `KnowledgeAssistantService` to a compatibility facade**

Keep the existing interface so coordinator tests and callers remain stable. `OpenAiCompatibleKnowledgeAssistantService` creates an `OpenAiModelGateway`, maps existing chat messages to `ModelMessage`, collects token events into `onDelta`, and throws a sanitized compatibility exception for `ModelEvent.Failed`.

Delete HTTP/SSE/JSON implementation details and parser helpers from the app module after adapter tests cover them.

- [ ] **Step 6: Run adapter and existing assistant tests**

```powershell
.\gradlew.bat :adapter:model-openai:test :app:testDebugUnitTest --tests "com.cslearningos.mobile.feature.assistant.*" --tests com.cslearningos.mobile.ui.LearningViewModelAssistantNavigationTest
```

Expected: PASS; existing prompts, streaming visibility, cancellation, and draft behavior are unchanged.

- [ ] **Step 7: Commit**

```powershell
git add android-app/adapter/model-openai android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/data/KnowledgeAssistantService.kt android-app/app/src/test/java/com/cslearningos/mobile/feature/assistant
git commit -m "refactor(android): isolate assistant model adapter"
```

## Task 6: Replace Byte-Size-Only Architecture Checks And Update State-Machine Contract

**Files:**
- Modify: `scripts/verify-android-architecture.ps1`
- Modify: `docs/state-machine.md`
- Modify: `android-app/docs/architecture.md`

- [ ] **Step 1: Add failing architecture checks**

Require the five project directories and inspect Kotlin imports under `domain/assistant` for these forbidden prefixes:

```powershell
$forbiddenDomainImports = @(
    "import android.",
    "import androidx.",
    "import com.cslearningos.mobile.ui.",
    "import com.cslearningos.mobile.data.",
    "import org.json."
)
```

Also fail if `android-app/app/build.gradle` contains `../content-demo`, if settings omit a required include, if `feature:assistant:impl` omits its API dependency, or if `adapter:model-openai` omits its domain dependency.

- [ ] **Step 2: Run the architecture script and verify it catches an intentionally queried legacy condition**

Before changing the script fully, confirm the new source-boundary check would fail against the old build path. Do not leave an intentional violation in the worktree.

- [ ] **Step 3: Implement deterministic module/import checks**

Keep legacy size thresholds as temporary compatibility warnings/checks, but make module existence, dependency direction, forbidden imports, and self-contained sources required failures. Output every offending file/import, not only a boolean.

- [ ] **Step 4: Update `docs/state-machine.md`**

Add an Android target-architecture section containing:

```markdown
## Android Modular Assistant Target State

The current `AssistantCoordinator` state is a compatibility implementation during Phase 1. Target ownership is split into Session, Run, Interaction, and Proposal aggregates. Model transport implements `ModelGateway`; it cannot persist Nodes, Quizzes, Captures, review state, or synchronization records.

Run transitions are correlated by `runId`. A stale, cancelled, interrupted, or superseded callback cannot advance the active run. A proposal stores the target base revision/hash and becomes a user-reviewed application command only after explicit acceptance.

The complete approved transitions, port matrix, replication lifecycle, and migration phases are defined in `docs/superpowers/specs/2026-07-13-android-layered-modular-architecture-design.md`.
```

- [ ] **Step 5: Link Android-owned architecture docs to the approved contract**

Document the module graph, current compatibility shell, exact architecture script command, and the fact that replication is not implemented in Phase 1.

- [ ] **Step 6: Run architecture and documentation tests**

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-android-architecture.ps1
Set-Location android-app
.\gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.docs.ProjectDocsTest --tests com.cslearningos.mobile.architecture.StandaloneAndroidBoundaryTest
```

Expected: PASS with the modular Assistant slice detected.

- [ ] **Step 7: Commit**

```powershell
git add scripts/verify-android-architecture.ps1 docs/state-machine.md android-app/docs/architecture.md
git commit -m "docs(android): enforce modular assistant contract"
```

## Task 7: Full Verification And Migration Handoff

**Files:**
- Modify only failures directly caused by Tasks 1-6.

- [ ] **Step 1: Run pure module tests**

```powershell
Set-Location android-app
.\gradlew.bat :core:kernel:test :domain:assistant:test :feature:assistant:impl:test :adapter:model-openai:test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run the full Android unit suite**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL with all existing and new tests passing.

- [ ] **Step 3: Assemble the app from the self-contained Android root**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL and starter assets generated from `android-app/starter-content`.

- [ ] **Step 4: Run repository architecture verification**

From repository root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-android-architecture.ps1
```

Expected: modular Assistant slice and standalone source checks pass.

- [ ] **Step 5: Inspect final dependency and worktree state**

```powershell
Set-Location android-app
.\gradlew.bat projects
Set-Location ..
git status --short
git log --oneline -8
```

Expected: required projects appear; only pre-existing unrelated untracked paths remain; Task 1-6 commits are present.

- [ ] **Step 6: Record Phase 1 completion**

Add a concise implementation note to `android-app/docs/architecture.md` listing the modules now active, compatibility code still in `:app`, and the next child project (`Phase 2: domain/Room model separation`).

- [ ] **Step 7: Commit the handoff note if changed**

```powershell
git add android-app/docs/architecture.md
git commit -m "docs(android): record phase one architecture baseline"
```

Skip this commit when the verification run required no handoff-note change.
