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

  testImplementation(project(":testing-common"))
  testImplementation("io.opentelemetry:opentelemetry-api")
}

tasks {
  jar {
    dependsOn(agent)
    from(zipTree(agent.singleFile))
    from(extensionLibs) {
      into("extensions")
    }

    doFirst {
      manifest.from(
        zipTree(agent.singleFile).matching {
          include("META-INF/MANIFEST.MF")
        }.singleFile,
      )
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
    "-javaagent:${file(agentJar).absolutePath}",
  )
}
