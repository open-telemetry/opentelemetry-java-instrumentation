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
    from(zipTree(agent.singleFile))
    from(extensionLibs) {
      into("extensions")
    }

    doFirst {
      manifest.from(
        zipTree(agent.singleFile).matching {
          include("META-INF/MANIFEST.MF")
        }.singleFile
      )
    }
  }

  afterEvaluate {
    withType<Test>().configureEach {
      dependsOn(jar)

      jvmArgs("-Dotel.javaagent.debug=true")
      jvmArgs("-javaagent:${jar.get().archiveFile.get().asFile.absolutePath}")
      jvmArgs("-Dotel.metrics.exporter=otlp")
    }
  }
}
