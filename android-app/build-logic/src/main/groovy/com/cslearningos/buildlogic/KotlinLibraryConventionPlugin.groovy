package com.cslearningos.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

class KotlinLibraryConventionPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.extensions.getByType(JavaPluginExtension).toolchain.languageVersion.set(
            JavaLanguageVersion.of(17)
        )
    }
}
