description = "Lombok compiler plugin (Common)"

plugins {
    id("common-configuration")
    id("test-federation-convention")
    id("com.autonomousapps.dependency-analysis")
    kotlin("jvm")
}

dependencies {
    compileOnly(project(":core:compiler.common"))
    compileOnly(project(":core:compiler.common.jvm"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { none() }
}

runtimeJar()
sourcesJar()
javadocJar()
