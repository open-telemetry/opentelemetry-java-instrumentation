plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "OpenTelemetry Javaagent for testing"
group = "io.opentelemetry.javaagent"

val agent = configurations.create("agent") {
  isCanBeResolved = true
  isCanBeConsumed = false
}

val extensionLibs = configurations.create("extensionLibs") {
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
  val extractAgent = register<ExtractJar>("extractAgent") {
    jarFile.from(agent)
    into(layout.buildDirectory.dir("extracted-agent"))
  }

  val testTemporalityDelta =
    register<Test>("testTemporalityDelta") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      filter {
        includeTestsMatching("io.opentelemetry.javaagent.testing.AgentMetricTemporalityTest")
      }
      systemProperty("otel.javaagent.testing.exporter.temporality", "delta")
    }

  val testTemporalityCumulative =
    register<Test>("testTemporalityCumulative") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      filter {
        includeTestsMatching("io.opentelemetry.javaagent.testing.AgentMetricTemporalityTest")
      }
      systemProperty("otel.javaagent.testing.exporter.temporality", "cumulative")
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

  check {
    dependsOn(testTemporalityDelta, testTemporalityCumulative)
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
