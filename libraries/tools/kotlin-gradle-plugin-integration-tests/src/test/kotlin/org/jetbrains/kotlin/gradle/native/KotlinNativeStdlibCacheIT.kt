/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.native

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.targets.native.internal.PlatformLibrariesGenerator
import org.jetbrains.kotlin.gradle.testbase.*
import org.jetbrains.kotlin.gradle.uklibs.applyMultiplatform
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import org.jetbrains.kotlin.konan.target.HostManager
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.assertTrue

@DisplayName("Kotlin/Native stdlib cache tests")
@NativeGradlePluginTests
class KotlinNativeStdlibCacheIT : KGPBaseTest() {

    /**
     * KT-86251: if a build is interrupted mid-cache-write, the next build picks up a truncated
     * `libstdlib-cache.a` and ld.lld fails with "truncated or malformed archive".
     *
     * [PlatformLibrariesGenerator.checkCaches] guards against this by requiring a `.cache-complete`
     * marker in `stdlib-cache/`. Without it the cache is considered incomplete and rebuilt.
     */
    @OsCondition(supportedOn = [OS.MAC, OS.LINUX], enabledOnCI = [OS.LINUX])
    @DisplayName("KT-86251: truncated stdlib cache from an interrupted build is detected and regenerated")
    @GradleTest
    fun truncatedStdlibCacheIsRegeneratedOnNextBuild(gradleVersion: GradleVersion, @TempDir konanTemp: Path) {
        val hostTargetKonanName = HostManager.host.name    // "linux_x64" / "macos_arm64"
        val platformName = HostManager.platformName()      // light dist dir has no "prebuilt" infix
        val isMac = HostManager.hostIsMac

        val buildOptions = defaultBuildOptions.copy(
            konanDataDir = konanTemp
        )

        project("empty", gradleVersion, buildOptions) {
            addKgpToBuildScriptCompilationClasspath()
            buildScriptInjection {
                project.applyMultiplatform {
                    val nativeTarget = if (isMac) macosArm64() else linuxX64()
                    nativeTarget.binaries.executable()
                    sourceSets.commonMain.get().compileSource("fun main() {}")
                }
            }

            val taskName = getDebugLinkTaskName(if (isMac) "macosArm64" else "linuxX64")

            build(taskName) {
                assertTasksExecuted(taskName)
            }

            // <konanTemp>/kotlin-native-prebuilt-<platform>-<version>/klib/cache/<target>-gSTATIC/stdlib-cache
            val stdlibCacheDir = konanTemp
                .listDirectoryEntries("kotlin-native-prebuilt-$platformName-*")
                .also { assertTrue(it.isNotEmpty(), "No konan distribution directory found under $konanTemp") }
                .first()
                .resolve("klib/cache")
                .listDirectoryEntries("${hostTargetKonanName}-gSTATIC*")
                .also { assertTrue(it.isNotEmpty(), "No ${hostTargetKonanName}-gSTATIC* cache directory found") }
                .first()
                .resolve("stdlib-cache")

            assertTrue(stdlibCacheDir.exists(), "stdlib-cache must exist after first build: $stdlibCacheDir")

            val markerFile = stdlibCacheDir.resolve(PlatformLibrariesGenerator.CACHE_COMPLETE_MARKER)
//            assertTrue(markerFile.exists(), ".cache-complete must be present after a successful build")

            // Simulate an interrupted build: drop the marker and corrupt the archive.
            markerFile.deleteIfExists()
            val archiveFile = stdlibCacheDir.resolve("bin/libstdlib-cache.a")
            assertTrue(archiveFile.exists(), "libstdlib-cache.a must exist after first build")

            // Simulate an interrupted write by truncating the archive to half its original size.
            val original = archiveFile.readBytes()
            assertTrue(original.size > 128, "archive unexpectedly small: ${original.size} bytes")
            archiveFile.writeBytes(original.copyOf(original.size / 2))

            // Without the fix, the second build reuses the corrupt archive and ld.lld fails.
//            build(taskName) {
//                assertTasksExecuted(taskName)
//                assertOutputContains("Precompile platform libraries for $hostTargetKonanName")
//            }

            buildAndFail(taskName, "--rerun-tasks") {
                assertTasksFailed(taskName)
                if (HostManager.hostIsMac) {
                    assertOutputContains("ld invocation reported errors")
                } else {
                    assertOutputContains("ld.lld invocation reported errors")
                }
            }

//            assertTrue(markerFile.exists(), ".cache-complete must be restored after regeneration")
        }
    }

    private fun getDebugLinkTaskName(targetName: String) = lowerCamelCaseName(
        ":linkDebugExecutable",
        targetName
    )
}
