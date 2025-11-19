plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "OpenTelemetry Javaagent for testing"
group = "io.opentelemetry.javaagent"

val agent by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}

val extensionLibs by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}

dependencies {
  extensionLibs(project(":testing:agent-exporter", configuration = "shadow"))
  agent(project(":javaagent", configuration = "baseJar"))

  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  testImplementation("io.opentelemetry:opentelemetry-api")
}

abstract class ExtractAgentJar : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val agentJar: ConfigurableFileCollection

  @get:OutputDirectory
  abstract val extractDir: DirectoryProperty

  @get:Inject
  abstract val archiveOperations: ArchiveOperations

  @get:Inject
  abstract val fileSystemOperations: FileSystemOperations

  @TaskAction
  fun extract() {
    fileSystemOperations.sync {
      from(archiveOperations.zipTree(agentJar.singleFile))
      into(extractDir)
    }
  }
}

abstract class ExtractAgentManifest : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val agentJar: ConfigurableFileCollection

  @get:OutputFile
  abstract val manifestFile: RegularFileProperty

  @get:Inject
  abstract val archiveOperations: ArchiveOperations

  @get:Inject
  abstract val fileSystemOperations: FileSystemOperations

  @TaskAction
  fun extract() {
    fileSystemOperations.copy {
      from(archiveOperations.zipTree(agentJar.singleFile))
      include("META-INF/MANIFEST.MF")
      into(temporaryDir)
    }
    manifestFile.get().asFile.writeBytes(
      temporaryDir.resolve("META-INF/MANIFEST.MF").readBytes()
    )
  }
}

val extractAgent = tasks.register<ExtractAgentJar>("extractAgent") {
  agentJar.from(agent)
  extractDir.set(layout.buildDirectory.dir("tmp/agent-extracted"))
}

val extractManifest = tasks.register<ExtractAgentManifest>("extractManifest") {
  agentJar.from(agent)
  manifestFile.set(layout.buildDirectory.file("tmp/agent-manifest/MANIFEST.MF"))
}

tasks {
  jar {
    dependsOn(extractAgent)
    from(extractAgent.flatMap { it.extractDir })
    from(extensionLibs) {
      into("extensions")
    }

    dependsOn(extractManifest)
    manifest.from(extractManifest.flatMap { it.manifestFile })
  }

  afterEvaluate {
    withType<Test>().configureEach {
      jvmArgs("-Dotel.javaagent.debug=true")

      jvmArgumentProviders.add(JavaagentProvider(jar.flatMap { it.archiveFile }))
    }
  }
}

class JavaagentProvider(
  @InputFile
  @PathSensitive(PathSensitivity.RELATIVE)
  val agentJar: Provider<RegularFile>,
) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> = listOf(
    "-javaagent:${agentJar.get().asFile.absolutePath}",
  )
}
