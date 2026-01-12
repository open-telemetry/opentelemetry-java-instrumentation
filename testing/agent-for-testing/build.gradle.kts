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

abstract class ExtractJar : Copy() {
  @get:InputFiles
  abstract val jarFile: ConfigurableFileCollection

  @get:Inject
  abstract val archiveOperations: ArchiveOperations

  init {
    from(jarFile.elements.map { files -> files.map { archiveOperations.zipTree(it) } })
  }
}

tasks {
  val extractAgent by registering(ExtractJar::class) {
    jarFile.from(agent)
    into(layout.buildDirectory.dir("extracted-agent"))
  }

  jar {
    from(extractAgent.map { it.outputs.files })
    from(extensionLibs) {
      into("extensions")
    }

    val manifestFileProvider = extractAgent.flatMap { task ->
      layout.buildDirectory.file("extracted-agent/META-INF/MANIFEST.MF")
    }

    doFirst {
      manifest.from(manifestFileProvider.get().asFile)
    }
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
