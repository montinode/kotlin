plugins {
    id("common-configuration")
    id("test-federation-convention")
    id("com.autonomousapps.dependency-analysis")
    java
    kotlin("jvm")
    id("test-inputs-check")
}

publish()
sourcesJar()
javadocJar()
configureKotlinCompileTasksGradleCompatibility()

dependencies {
    val coreDepsVersion = libs.versions.kotlin.`for`.gradle.plugins.compilation.get()
    implementation(kotlin("stdlib", coreDepsVersion))
    implementation(commonDependency("com.google.code.gson:gson"))
    testImplementation(kotlin("stdlib", coreDepsVersion))
    testImplementation(kotlin("test-junit", coreDepsVersion))
}
