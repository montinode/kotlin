import org.jetbrains.kotlin.build.foreign.CheckForeignClassUsageTask

plugins {
    `java-library`
    id("kotlin-git.gradle-build-conventions.foreign-class-usage-checker")
    id("analysis-api-artifact")
}

@Suppress("UNCHECKED_CAST")
val analysisApiSurfaceDependencies: Array<String> = rootProject.extra["analysisApiSurfaceDependencies"] as Array<String>

val analysisApiSurfaceProjects = listOf(
    ":analysis:analysis-api",
    ":analysis:analysis-api-standalone",
)

dependencies {
    for (projectPath in analysisApiSurfaceDependencies + analysisApiSurfaceProjects) {
        embedded(project(projectPath)) { isTransitive = false }
    }

    api(project(":prepare:analysis-api:kotlin-analysis-api-intellij-api-surface-components"))
}

val checkForeignClassUsage = tasks.register("checkForeignClassUsage", CheckForeignClassUsageTask::class) {
    classes.from(tasks.jar)
    classpath.from(configurations.runtimeClasspath)
    missingClasspathEntriesOutputFile = file("api/analysis-api-surface.classpath-issues")
}
