/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.android.externalAndroidTarget

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerArgumentsProducer
import org.jetbrains.kotlin.gradle.testbase.*
import org.jetbrains.kotlin.gradle.uklibs.ignoreAccessViolations
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Used AGP 9.0 as the minimal stable version supported for the android library
@AndroidTestVersions(minVersion = TestVersions.AGP.AGP_90)
@AndroidGradlePluginTests
class AndroidCompilerOptionsExternalAndroidTargetIT : KGPBaseTest() {

    // Uses `com.android.kotlin.multiplatform.library`, requires AGP new DSL.
    override val defaultBuildOptions: BuildOptions
        get() = super.defaultBuildOptions.copy(enableLegacyAgpDsl = false)

    @GradleAndroidTest
    fun `androidLibrary compilerOptions propagate to Android compilation`(
        gradleVersion: GradleVersion, androidVersion: String, jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        externalAndroidLibraryProject(
            gradleVersion = gradleVersion,
            androidVersion = androidVersion,
            jdkVersion = jdkVersion,
            namespace = "org.jetbrains.sample.options",
            androidLibraryConfiguration = """
                compilerOptions {
                    optIn.add("kotlin.RequiresOptIn")
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                    progressiveMode.set(true)
                    allWarningsAsErrors.set(true)
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
                }
            """.trimIndent(),
        ) {
            buildScriptInjection {
                kotlinMultiplatform.apply {
                    iosArm64()

                    sourceSets.getByName("androidMain").compileSource(
                        """
                        package sample
                        class SimpleSource
                        """.trimIndent()
                    )
                }
            }
            val compilerArguments = compilerArguments(":compileAndroidMain")
            compilerArguments.assertHasOptIn("kotlin.RequiresOptIn")
            compilerArguments.assertHasFreeCompilerArg("-Xexpect-actual-classes")
            compilerArguments.assertProgressiveMode()
            compilerArguments.assertAllWarningsAsErrors()
            compilerArguments.assertJvmTarget("1.8")
        }
    }

    @GradleAndroidTest
    fun `kotlinMultiplatform compilerOptions propagate to Android compilation`(
        gradleVersion: GradleVersion, androidVersion: String, jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        externalAndroidLibraryProject(
            gradleVersion = gradleVersion,
            androidVersion = androidVersion,
            jdkVersion = jdkVersion,
            namespace = "org.jetbrains.sample.options",
        ) {
            buildScriptInjection {
                kotlinMultiplatform.apply {
                    iosArm64()

                    sourceSets.getByName("androidMain").compileSource(
                        """
                        package sample
                        class SimpleSource
                        """.trimIndent()
                    )

                    compilerOptions {
                        progressiveMode.set(true)
                        allWarningsAsErrors.set(true)
                        optIn.add("kotlin.RequiresOptIn")
                        freeCompilerArgs.add("-Xexpect-actual-classes")
                    }
                }
            }
            val compilerArguments = compilerArguments(":compileAndroidMain")
            compilerArguments.assertHasOptIn("kotlin.RequiresOptIn")
            compilerArguments.assertHasFreeCompilerArg("-Xexpect-actual-classes")
            compilerArguments.assertProgressiveMode()
            compilerArguments.assertAllWarningsAsErrors()
        }
    }

    @GradleAndroidTest
    fun `compilation-level compilerOptions propagate to Android compilation`(
        gradleVersion: GradleVersion, androidVersion: String, jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        externalAndroidLibraryProject(
            gradleVersion = gradleVersion,
            androidVersion = androidVersion,
            jdkVersion = jdkVersion,
            namespace = "org.jetbrains.sample.options",
            androidLibraryConfiguration = """
                compilations.getByName("main").compileTaskProvider.configure {
                    compilerOptions {
                        progressiveMode.set(true)
                        allWarningsAsErrors.set(true)
                    }
                }
            """.trimIndent(),
        ) {
            buildScriptInjection {
                kotlinMultiplatform.apply {
                    iosArm64()

                    sourceSets.getByName("androidMain").compileSource(
                        """
                        package sample
                        class CompilationLevelProbe
                        """.trimIndent()
                    )
                }
            }
            val compilerArguments = compilerArguments(":compileAndroidMain")
            compilerArguments.assertProgressiveMode()
            compilerArguments.assertAllWarningsAsErrors()
        }
    }

    @GradleAndroidTest
    fun `compilation-level compilerOptions propagate to Android host test compilation`(
        gradleVersion: GradleVersion, androidVersion: String, jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        externalAndroidLibraryProject(
            gradleVersion = gradleVersion,
            androidVersion = androidVersion,
            jdkVersion = jdkVersion,
            namespace = "org.jetbrains.sample.options",
            androidLibraryConfiguration = """
                withHostTest {}
                compilations.getByName("hostTest").compileTaskProvider.configure {
                    compilerOptions {
                        progressiveMode.set(true)
                        allWarningsAsErrors.set(true)
                    }
                }
            """.trimIndent(),
        ) {
            buildScriptInjection {
                kotlinMultiplatform.apply {
                    iosArm64()

                    sourceSets.getByName("androidHostTest").compileSource(
                        """
                        package sample
                        class HostCompilationLevelProbe
                        """.trimIndent()
                    )
                }
            }
            val compilerArguments = compilerArguments(":compileAndroidHostTest")
            compilerArguments.assertProgressiveMode()
            compilerArguments.assertAllWarningsAsErrors()
        }
    }

    @GradleAndroidTest
    fun `androidLibrary compilerOptions override kotlinMultiplatform allWarningsAsErrors`(
        gradleVersion: GradleVersion, androidVersion: String, jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        externalAndroidLibraryProject(
            gradleVersion = gradleVersion,
            androidVersion = androidVersion,
            jdkVersion = jdkVersion,
            namespace = "org.jetbrains.sample.options",
            androidLibraryConfiguration = """
                compilerOptions {
                    allWarningsAsErrors.set(false)
                }
            """.trimIndent(),
        ) {
            buildScriptInjection {
                kotlinMultiplatform.apply {
                    compilerOptions {
                        allWarningsAsErrors.set(true)
                    }
                    iosArm64()

                    sourceSets.getByName("androidMain").compileSource(
                        """
                        package sample
                        class OverrideProbe
                        """.trimIndent()
                    )
                }
            }
            compilerArguments(":compileAndroidMain").assertNoAllWarningsAsErrors()
        }
    }

    private fun externalAndroidLibraryProject(
        gradleVersion: GradleVersion,
        androidVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
        namespace: String,
        androidLibraryConfiguration: String = "",
        configureProject: TestProject.() -> Unit = {},
    ): TestProject = project(
        "empty",
        gradleVersion = gradleVersion,
        buildOptions = defaultBuildOptions.copy(androidVersion = androidVersion),
        buildJdk = jdkVersion.location,
    ) {
        buildGradle.toFile().delete()
        buildGradleKts.toFile().writeText(
            """
            plugins {
                kotlin("multiplatform")
                id("com.android.kotlin.multiplatform.library")
            }

            kotlin {
                androidLibrary {
                    compileSdk = 34
                    namespace = "$namespace"
            ${androidLibraryConfiguration.trim().prependIndent("        ")}
                }
            }
            """.trimIndent()
        )
        configureProject()
    }

    private fun TestProject.compilerArguments(taskPath: String): Map<String, Any> =
        buildScriptReturn {
            project.ignoreAccessViolations {
                val taskName = taskPath.substringAfterLast(":")
                val task = project.tasks.named(taskName, KotlinCompile::class.java).get()
                val arguments = task.createCompilerArguments(KotlinCompilerArgumentsProducer.CreateCompilerArgumentsContext.default)
                    as K2JVMCompilerArguments

                mapOf(
                    "optIn" to arguments.optIn.orEmpty().toList(),
                    "freeArgs" to arguments.freeArgs.orEmpty().toList(),
                    "progressiveMode" to arguments.progressiveMode,
                    "allWarningsAsErrors" to arguments.allWarningsAsErrors,
                    "jvmTarget" to arguments.jvmTarget.orEmpty(),
                )
            }
        }.buildAndReturn(
            taskPath,
            configurationCache = BuildOptions.ConfigurationCacheValue.DISABLED,
            buildAction = BuildActions.buildWithAssertions {
                assertTasksExecuted(taskPath)
            }
        )

    private fun Map<String, Any>.assertHasOptIn(value: String) {
        assertTrue(value in optIns(), "Expected opt-in '$value' in ${optIns()}")
    }

    private fun Map<String, Any>.assertHasFreeCompilerArg(value: String) {
        assertTrue(value in freeCompilerArgs(), "Expected free compiler arg '$value' in ${freeCompilerArgs()}")
    }

    private fun Map<String, Any>.assertProgressiveMode() {
        assertTrue(progressiveMode(), "Expected progressive mode to be enabled")
    }

    private fun Map<String, Any>.assertAllWarningsAsErrors() {
        assertTrue(allWarningsAsErrors(), "Expected allWarningsAsErrors to be enabled")
    }

    private fun Map<String, Any>.assertNoAllWarningsAsErrors() {
        assertFalse(allWarningsAsErrors(), "Expected allWarningsAsErrors to be disabled")
    }

    private fun Map<String, Any>.assertJvmTarget(expected: String) {
        assertEquals(expected, jvmTarget(), "Unexpected jvmTarget")
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.optIns(): List<String> = getValue("optIn") as List<String>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.freeCompilerArgs(): List<String> = getValue("freeArgs") as List<String>

    private fun Map<String, Any>.progressiveMode(): Boolean = getValue("progressiveMode") as Boolean

    private fun Map<String, Any>.allWarningsAsErrors(): Boolean = getValue("allWarningsAsErrors") as Boolean

    private fun Map<String, Any>.jvmTarget(): String = getValue("jvmTarget") as String
}
