import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.zip.ZipFile

plugins {
    java
    kotlin("jvm")
    id("java-test-fixtures")
    id("project-tests-convention")
    id("test-inputs-check")
}

description = "Runner for Swift Export (for embedding purpose)"

publish()

val validateSwiftExportEmbeddable by tasks.registering

// A resolvable configuration representing the full runtime graph of the AA facade artifact.
val analysisApiRuntimeClasspath = configurations.detachedConfiguration(
    dependencies.create(project(":prepare:analysis-api:kotlin-analysis-api"))
).apply {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}

dependencies {
    embedded(project(":native:swift:sir")) { isTransitive = false }
    embedded(project(":native:swift:sir-light-classes")) { isTransitive = false }
    embedded(project(":native:swift:sir-printer")) { isTransitive = false }
    embedded(project(":native:swift:sir-providers")) { isTransitive = false }
    embedded(project(":native:swift:swift-export-standalone")) { isTransitive = false }
    embedded(project(":libraries:tools:analysis-api-based-klib-reader")) { isTransitive = false }
    embedded(project(":native:analysis-api-based-export-common")) { isTransitive = false }

    // Analysis API, embedded via the published artifact structure (KT-61404)
    embedded(project(":prepare:analysis-api:kotlin-analysis-api-surface")) { isTransitive = false }
    embedded(project(":prepare:analysis-api:kotlin-analysis-api-platform-interface")) { isTransitive = false }
    embedded(project(":prepare:analysis-api:kotlin-analysis-api-implementation")) { isTransitive = false }
    embedded(project(":prepare:analysis-api:kotlin-analysis-api-intellij-api-surface-components")) { isTransitive = false }
    embedded(project(":prepare:analysis-api:kotlin-analysis-api-intellij-implementation-components")) { isTransitive = false }

    // Analysis API decompiled dependencies — not part of the published kotlin-analysis-api artifacts
    embedded(project(":analysis:decompiled:decompiler-native")) { isTransitive = false }
    embedded(project(":analysis:decompiled:decompiler-to-psi")) { isTransitive = false }
    embedded(project(":analysis:decompiled:light-classes-for-decompiled")) { isTransitive = false }
    embedded(project(":analysis:decompiled:decompiler-to-stubs")) { isTransitive = false }
    embedded(project(":analysis:decompiled:decompiler-to-file-stubs")) { isTransitive = false }

    // Take only EXTERNAL modules (skip project components — those AA jars are embedded explicitly),
    // and skip deps that must stay on the runtime classpath only (not embedded):
    //  - stdlib: addEmbeddedRuntime forbids embedding it
    //  - kotlinx-serialization: not in packagesToRelocate; provided as runtimeOnly below
    //  - org.jetbrains:annotations: annotation-only, duplicates against runtime classpath
    val inheritedExternalAnalysisApiDeps = analysisApiRuntimeClasspath.incoming.resolutionResult.allComponents
        .mapNotNull { it.id as? ModuleComponentIdentifier }
        .map { "${it.group}:${it.module}:${it.version}" }
        .filterNot { it.startsWith("org.jetbrains.kotlin:kotlin-stdlib") }
        .filterNot { it.startsWith("org.jetbrains.kotlinx:kotlinx-serialization") }
        .filterNot { it.startsWith("org.jetbrains:annotations") }

    inheritedExternalAnalysisApiDeps.forEach { gav ->
        embedded(dependencies.create(gav).apply {
            (this as? ExternalModuleDependency)?.isTransitive = false
        })
    }

    runtimeOnly(kotlinStdlib())
    runtimeOnly(libs.kotlinx.serialization.core)
}

fun registerSwiftExportEmbeddableValidationTasks(swiftExportEmbeddableJarTask: TaskProvider<out org.gradle.jvm.tasks.Jar>) {
    val runtimeClasspathCopy = configurations.runtimeClasspath.get().copyRecursive()
    runtimeClasspathCopy.dependencies.addAll(
        listOf(
            /**
             * These are needed for .kts files analysis; we don't actually want them in Swift Export, but currently ProGuard sees these in
             * shared Analysis API code and complaints
             */
            dependencies.create(project(":kotlin-scripting-compiler-embeddable")),
            dependencies.create(project(":kotlin-assignment-compiler-plugin.embeddable")),
        )
    )

    // FIXME: Publish the ProGuarded version of swift-export-embeddable - KT-69180
    val validateSwiftExportEmbeddableHasProperDependenciesInTheClasspath = validateEmbeddedJarClasspathUsingProguard(
        swiftExportEmbeddableJarTask,
        files(runtimeClasspathCopy),
        proguardConfiguration = project.layout.projectDirectory.file("swift-export-embeddable.pro")
    )
    validateSwiftExportEmbeddable.configure { dependsOn(validateSwiftExportEmbeddableHasProperDependenciesInTheClasspath) }

    val validateNoDuplicatesInRuntimeClasspath = validateEmbeddedJarRuntimeClasspathHasNoDuplicates(
        swiftExportEmbeddableJarTask,
        files(runtimeClasspathCopy),
    )
    validateSwiftExportEmbeddable.configure { dependsOn(validateNoDuplicatesInRuntimeClasspath) }
}

sourceSets {
    "main" { none() }
}

val swiftExportEmbeddableJar = runtimeJarWithRelocation {
    configureEmbeddableCompilerRelocation()
    // Scripting classes are embedded in kotlin-analysis-api-implementation (via additionalCompilerProjects)
    // but must not be in the fatjar — they are provided at runtime via kotlin-scripting-compiler-embeddable.
    exclude("kotlin/script/**")
    exclude("org/jetbrains/kotlin/scripting/**")
    // org.jetbrains:annotations is annotation-only; already on the runtime classpath transitively.
    exclude("org/jetbrains/annotations/**")
    exclude("org/intellij/lang/annotations/**")
}
registerSwiftExportEmbeddableValidationTasks(swiftExportEmbeddableJar)

sourcesJar { exclude("**") } // empty Jar, no public sources
javadocJar { exclude("**") } // empty Jar, no public javadocs

/**
 * Run swift-export-standalone tests against swift-export-embeddable and its runtime classpath to reproduce the environment in SwiftExportAction
 *
 * If these tests fail with ClassNotFoundException or similar it means that either:
 * - Some change introduced runtime classpath breakage in swift-export-embeddable. This means such dependency must be added to [runtimeOnly]
 * , [embedded] dependencies of swift-export-embeddable or relevant classes must be retained in compiler.pro
 * - Or test classes and the testing code is missing a dependency in [shadedIntransitiveTestDependenciesJar]. Please add dependencies
 * carefully after understanding the sources of breakage
 *
 * Make sure to run these tests against ProGuarded kotlin-compiler-embeddable e.g.:
 * ./gradlew :native:swift:swift-export-embeddable:test --info -Pkotlin.native.enabled=true -Pteamcity=true
 */

dependencies {
    testFixturesImplementation(testFixtures(project(":native:swift:swift-export-standalone-integration-tests:simple")))
    testFixturesImplementation(testFixtures(project(":native:swift:swift-export-standalone-integration-tests:coroutines")))
    testFixturesImplementation(testFixtures(project(":generators:test-generator")))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(testFixtures(project(":native:swift:swift-export-standalone-integration-tests")))
    testImplementation(testFixtures(project(":native:swift:swift-export-standalone-integration-tests:external")))
    testImplementation(testFixtures(project(":compiler:tests-common")))
    testImplementation(testFixtures(project(":compiler:tests-common-new")))
}

sourceSets {
    "test" { projectDefault() }
    "testFixtures" { projectDefault() }
}

val intransitiveTestDependenciesJars = configurations.detachedConfiguration().apply {
    /**
     * These dependencies are used by the execution of test classes in swift-export-standalone
     *
     * Please read the comment above before adding dependencies here
     */
    isTransitive = false
    // gson is actually also shadowed and embedded in KGP. In these tests it is used in XcRunRuntimeUtils
    dependencies.add(project.dependencies.create(commonDependency("com.google.code.gson:gson")))
    dependencies.add(project.dependencies.create(commonDependency("org.apache.commons:commons-lang3")))
    dependencies.add(project.dependencies.project(":native:executors"))
    dependencies.add(project.dependencies.project(":kotlin-compiler-runner-unshaded"))
    dependencies.add(project.dependencies.project(":kotlin-test"))
    dependencies.add(project.dependencies.project(":native:external-projects-test-utils"))

    dependencies.add(project.dependencies.testFixtures(project(":native:native.tests")))
    dependencies.add(project.dependencies.testFixtures(project(":compiler:tests-compiler-utils")))
    dependencies.add(project.dependencies.testFixtures(project(":compiler:tests-common")))
    dependencies.add(project.dependencies.testFixtures(project(":compiler:tests-common-new")))
    dependencies.add(project.dependencies.testFixtures(project(":compiler:test-infrastructure")))
    dependencies.add(project.dependencies.testFixtures(project(":compiler:test-infrastructure-utils")))
    dependencies.add(project.dependencies.testFixtures(project(":compiler:test-infrastructure-utils.common")))

    dependencies.add(project.dependencies.testFixtures(project(":native:swift:swift-export-standalone-integration-tests")))
}

val shadedIntransitiveTestDependenciesJar = tasks.register<ShadowJar>("shadedTestDependencies") {
    destinationDirectory.set(project.layout.buildDirectory.dir("testDependenciesShaded"))
    configurations.add(intransitiveTestDependenciesJars)
    from(testSourceSet.output)
    configureEmbeddableCompilerRelocation()
    // ShadowJar doesn't handle duplicates from embedded jars
    // duplicatesStrategy = DuplicatesStrategy.FAIL
    val intransitiveTestDependenciesJarFiles = files(intransitiveTestDependenciesJars)
    doFirst {
        val permittedDuplicates = setOf(
            "META-INF/MANIFEST.MF",
            "META-INF/versions/9/module-info.class",
            "com/intellij/testFramework/TestDataPath.class",
        )
        val duplicates = intransitiveTestDependenciesJarFiles.flatMap { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().filterNot { it.isDirectory || it.name in permittedDuplicates }.map { it.name }.toList()
            }.map { path ->
                path to jar
            }
        }.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            error(duplicates.map { "${it.key}:\n${it.value.joinToString("\n") { "  ${it}" }}" }.joinToString("\n\n"))
        }
    }
}

val transitiveTestRuntimeClasspath = configurations.detachedConfiguration().apply {
    dependencies.add(libs.junit.jupiter.engine.get())
    dependencies.add(libs.junit.platform.launcher.get())
}

projectTests {
    testData(project(":native:swift:swift-export-standalone-integration-tests:simple").isolated, "testData")
    testData(project(":native:swift:swift-export-standalone-integration-tests:external").isolated, "testData")
    testData(project(":native:swift:swift-export-standalone-integration-tests:coroutines").isolated, "testData")
    testData(rootProject.isolated, "native/native.tests/testData/framework")

    testGenerator(
        "org.jetbrains.kotlin.swiftexport.standalone.embeddable.TestGeneratorKt",
        generateTestsInBuildDirectory = true,
    )

    nativeTestTaskWithExternalDependencies(
        "test",
        requirePlatformLibs = true,
        allowUnsafe = true, // KT-85212
    ) {
        classpath = files(
            // swift-export-embeddable and its runtime dependencies is what KGP will see in SwiftExportAction
            swiftExportEmbeddableJar,
            configurations.runtimeClasspath,
            // These dependencies are used by the test classes
            shadedIntransitiveTestDependenciesJar,
            transitiveTestRuntimeClasspath,
            configurations.testRuntimeClasspath, // Includes KotlinSecurityManager from test-inputs-check
        )
        testClassesDirs = testSourceSet.output.classesDirs
        extensions.configure<TestInputsCheckExtension>("testInputsCheck") {
            allowFlightRecorder.set(true)
        }
    }
}
