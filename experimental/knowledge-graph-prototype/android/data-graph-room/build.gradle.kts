/*
 * data:graph-room —— KnowledgeGraph 的 Room 持久化适配层(RFC: docs/RFC-knowledge-graph.md)。
 *
 * 定位: CS Learning OS(Room schema v8)的纯增量模块, 只新增 kg_* 五表(v9), 不动旧表。
 * 依赖方向: 本模块只依赖 Room/coroutines/serialization; 对现有 data:content-room 的
 *           processed_commands / replication_outbox 复用通过本地端口(ProcessedCommandPort /
 *           OutboxPort)注入, 由 app 组合根桥接, 避免跨模块 DAO 耦合。
 *
 * 注意: 版本号请与根项目版本目录(libs.versions.toml)对齐后收敛; 此处按可独立装配给出。
 */
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.cslearningos.graph.data"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Room 2.6.1: @Upsert 稳定可用; TableInfo 校验行为已按该版本源码核对(见 Migration8To9 KDoc)
    api("androidx.room:room-runtime:2.6.1")
    api("androidx.room:room-ktx:2.6.1") // withTransaction / Flow 查询
    ksp("androidx.room:room-compiler:2.6.1")

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // outbox payload 序列化

    // 可选测试骨架(Robolectric + in-memory Room, 无仪器测试需求 —— RFC §5)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
