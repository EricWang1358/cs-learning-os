plugins {
    kotlin("jvm") version "1.9.24"
}

group = "com.cslearningos.graph"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // 纯 Kotlin 领域模块: 仅允许 kotlin-stdlib + kotlinx-coroutines + JUnit5
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
