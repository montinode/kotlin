plugins {
    kotlin("jvm")
}

@Suppress("UNCHECKED_CAST")
val commonCompilerModules: Array<String> = rootProject.extra["commonCompilerModules"] as Array<String>
@Suppress("UNCHECKED_CAST")
val descriptorsCompilerModules: Array<String> = rootProject.extra["descriptorsCompilerModules"] as Array<String>

/**
 * The list of modules that aren't a part of [commonCompilerModules] and doesn't have a dedicated artifact,
 * but still somewhere between the PSI and the Analysis API implementations. Mostly related to PSI.
 */
val otherAnalysisApiModules = listOf(
    ":analysis:decompiled:decompiler-js",
    ":analysis:decompiled:decompiler-native",
    ":analysis:decompiled:decompiler-to-file-stubs",
    ":analysis:decompiled:decompiler-to-psi",
    ":analysis:decompiled:decompiler-to-stubs",
    ":analysis:decompiled:light-classes-for-decompiled",
    ":analysis:stubs",
)

val projects = commonCompilerModules.asList() + descriptorsCompilerModules + otherAnalysisApiModules + listOf(
    ":compiler:arguments.common",
    ":compiler:cli-base",
    ":kotlin-build-common",
    ":kotlin-compiler-runner-unshaded",
    ":kotlin-preloader",
    ":daemon-common",
    ":kotlin-daemon-client",
    ":compiler:build-tools:kotlin-build-tools-api",
)

publishJarsForIde(
    projects = projects,
    libraryDependencies = listOf(protobufFull())
)
