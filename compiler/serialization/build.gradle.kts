plugins {
    id("common-configuration")
    id("test-federation-convention")
    id("com.autonomousapps.dependency-analysis")
    kotlin("jvm")
}

dependencies {
    implementation(project(":compiler:resolution"))
    implementation(project(":core:deserialization"))
    implementation(project(":core:descriptors"))
    api(project(":compiler:util"))

    compileOnly(intellijCore())
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}

optInToK1Deprecation()
