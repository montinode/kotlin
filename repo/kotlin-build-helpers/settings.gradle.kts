rootProject.name = "kotlin-build-helpers"

pluginManagement {
    repositories {
        maven("https://redirector.kotlinlang.org/maven/dev") {
            content {
                includeGroupByRegex("org.jetbrains.kotlin.*")
            }
        }
        gradlePluginPortal()
    }
}
