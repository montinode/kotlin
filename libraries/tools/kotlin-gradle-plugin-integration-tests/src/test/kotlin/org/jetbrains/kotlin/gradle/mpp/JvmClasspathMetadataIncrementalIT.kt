/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.mpp

import org.gradle.api.logging.LogLevel
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.testbase.*
import org.junit.jupiter.api.DisplayName

@MppGradlePluginTests
@DisplayName("JVM classpath metadata incremental compilation")
class JvmClasspathMetadataIncrementalIT : KGPBaseTest() {

    override val defaultBuildOptions: BuildOptions
        get() = super.defaultBuildOptions.copy(
            logLevel = LogLevel.DEBUG,
            languageVersion = "2.0",
            enableUnsafeIncrementalCompilationForMultiplatform = true,
        )

    @GradleTest
    @DisplayName("Verify that incremental compilation without JVM classpath metadata leads to a failure")
    fun testWithJvmClasspathMetadataDisabled(gradleVersion: GradleVersion) {
        project(
            projectName = "jvm-classpath-metadata-incremental",
            gradleVersion = gradleVersion,
            buildOptions = defaultBuildOptions.copy(jvmClasspathMetadata = false)
        ) {
            build("compileKotlinJvm") {
                assertTasksExecuted(":compileKotlinJvm")
            }

            projectPath.resolve("src/commonMain/kotlin/foo.kt").modify { content ->
                content.replace("fun foo() = bar(42)", "fun foo() = bar(41)")
            }

            buildAndFail("compileKotlinJvm") {
                assertTasksFailed(":compileKotlinJvm")
            }
        }
    }

    @GradleTest
    @DisplayName("Verify that incremental compilation with JVM classpath metadata succeeds")
    fun testWithJvmClasspathMetadataEnabled(gradleVersion: GradleVersion) {
        project(
            projectName = "jvm-classpath-metadata-incremental",
            gradleVersion = gradleVersion,
            buildOptions = defaultBuildOptions.copy(jvmClasspathMetadata = true)
        ) {
            build("compileKotlinJvm") {
                assertTasksExecuted(":compileKotlinJvm")
            }

            projectPath.resolve("src/commonMain/kotlin/foo.kt").modify { content ->
                content.replace("fun foo() = bar(42)", "fun foo() = bar(41)")
            }

            build("compileKotlinJvm") {
                assertTasksExecuted(":compileKotlinJvm")
            }
        }
    }
}
