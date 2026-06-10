import com.github.gradle.node.npm.task.NpmTask

plugins {
  alias(libs.plugins.gradle.node)
  base
}

description = "Node utils"

node {
    download.set(true)
}

val deployDir = "$buildDir/deploy_to_npm"
val templateDir = "$projectDir/templates"
val kotlincDir = "$projectDir/../../dist/kotlinc"

fun getProperty(name: String, default: String = "") = findProperty(name)?.toString() ?: default

val deployVersion = getProperty("kotlin.deploy.version", "0.0.0")
val deployTag = getProperty("kotlin.deploy.tag", "dev")
val authToken = getProperty("kotlin.npmjs.auth.token")
val dryRun = getProperty("dryRun", "false") // Pack instead of publish

fun Project.createCopyTemplateTask(templateName: String): Copy {
  return task<Copy>("copy-$templateName-template") {
      from("$templateDir/$templateName")
      into("$deployDir/$templateName")

      expand(hashMapOf("version" to deployVersion))
  }
}

fun Project.createValidateTokenTask(templateName: String): NpmTask {
  return task<NpmTask>("validate-npm-token-for-$templateName") {
    val deployDir = File("$deployDir/$templateName")
    workingDir.set(deployDir)

    // During dry run, make an authenticated npm request (`whoami`) using the auth token
    // to validate that the token works against the registry.
    // `npm whoami` returns the user associated with the token and fails if the token is invalid.
    args.set(
      listOf(
        "whoami",
        "--registry=https://registry.npmjs.org/",
        "--//registry.npmjs.org/:_authToken=$authToken"
      )
    )
  }
}

fun Project.createPublishToNpmTask(templateName: String): NpmTask {
  return task<NpmTask>("publish-$templateName-to-npm") {
    val deployDir = File("$deployDir/$templateName")
    workingDir.set(deployDir)

    val deployArgs = listOf("publish", "--//registry.npmjs.org/:_authToken=$authToken", "--tag=$deployTag")
    if (dryRun == "true") {
      println("$deployDir \$ npm arguments: $deployArgs");
      // During dry run, instead of actually publishing, produce the package tarball (`pack`).
      // Token validation is performed separately by the `validate-npm-token-for-*` task,
      // which this task depends on (see usage below).
      args.set(listOf("pack"))
      dependsOn(createValidateTokenTask(templateName))
    }
    else {
      args.set(deployArgs)
    }
  }
}

fun sequential(first: Task, vararg tasks: Task): Task {
  tasks.fold(first) { previousTask, currentTask ->
    currentTask.dependsOn(previousTask)
  }
  return tasks.last()
}

val publishKotlinCompiler = sequential(
  createCopyTemplateTask("kotlin-compiler"),
  task<Copy>("copy-kotlin-compiler") {
    from(kotlincDir)
    into("$deployDir/kotlin-compiler")
  },
  task<Exec>("chmod-kotlinc-bin") {
    commandLine = listOf("chmod", "-R", "ugo+rx", "$deployDir/kotlin-compiler/bin")
  },
  createPublishToNpmTask("kotlin-compiler")
)

task("publishAll") {
    dependsOn(publishKotlinCompiler)
}
