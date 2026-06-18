plugins {
    id("java-flight-recorder")
}

val pluginBuildDir = "test-inputs-check-v2"
val testInputsCheck = extensions.create<TestInputsCheckExtensionV2>("testInputsCheck")

afterEvaluate {
    if (testInputsCheck.enabled.get()) {
        tasks.withType<Test>().names.forEach { testTaskName ->
            val testTask = tasks.named<Test>(testTaskName)
            val declaredInputsFile = layout.buildDirectory.file("$pluginBuildDir/declared-inputs-for-${testTask.name}.txt")
            val undeclaredInputsFile = layout.buildDirectory.file("$pluginBuildDir/undeclared-inputs-for-${testTask.name}.txt")

            val checkTestInputsTask = registerCheckTestInputsTask(testTask, declaredInputsFile, undeclaredInputsFile)
            configureTestTask(testTask, checkTestInputsTask, declaredInputsFile)
        }
    }
}

fun registerCheckTestInputsTask(
    testTask: TaskProvider<Test>,
    declaredInputsFile: Provider<RegularFile>,
    undeclaredInputsFile: Provider<RegularFile>,
) =
    tasks.register<CheckTestInputs>("checkInputsFor${testTask.name.capitalize()}") {
        group = "verification"
        description = "Check undeclared inputs from task ${testTask.name}"
        this.jfrFile.from(testTask.map { it.javaFlightRecorder.jfrFile })
        this.declaredInputsFile.set(declaredInputsFile)
        this.undeclaredInputsFile.set(undeclaredInputsFile)
        this.verificationTasksDisabled.value(kotlinBuildProperties.verificationTasksDisabled).finalizeValue()
        this.teamcityBuild.value(kotlinBuildProperties.isTeamcityBuild).finalizeValue()
    }

fun configureTestTask(
    testTask: TaskProvider<Test>,
    checkTestInputsTask: TaskProvider<CheckTestInputs>,
    declaredInputsFile: Provider<RegularFile>,
) {
    testTask.configure {
        systemProperty("test.instrumenter.inputs.check.enabled", true)
        addAbsoluteFileProperty(declaredInputsFile, "test.instrumenter.declared.inputs.file")
        addAbsoluteDirectoryProperty(layout.settingsDirectory, "test.instrumenter.root.dir")
        addAbsoluteDirectoryProperty(layout.buildDirectory, "test.instrumenter.build.dir")
        addLazyBooleanSystemProperty(testInputsCheck.failFast, "test.instrumenter.fail.fast")

        doFirst {
            declaredInputsFile.get().asFile.apply {
                parentFile.mkdirs()
                writeText(inputs.files.asFileTree.joinToString(separator = "\n"))
            }
        }

        if (enabled) {
            finalizedBy(checkTestInputsTask)
        }
    }
}

