plugins {
  `maven-publish`
  signing
}

publishing {
  publications {
    register<MavenPublication>("maven") {
      plugins.withId("java-platform") {
        from(components["javaPlatform"])
      }
      plugins.withId("java-library") {
        from(components["java"])
      }

      versionMapping {
        allVariants {
          fromResolutionResult()
        }
      }

      if (findProperty("otel.stable") != "true") {
        val versionParts = version.toString().split('-').toMutableList()
        versionParts[0] += "-alpha"
        version = versionParts.joinToString("-")
      }

      afterEvaluate {
        val mavenGroupId: String? by project
        if (mavenGroupId != null) {
          groupId = mavenGroupId
        }
        artifactId = artifactPrefix(project, base.archivesName.get()) + base.archivesName.get()

        if (!groupId.startsWith("io.opentelemetry.")) {
          throw GradleException("groupId is not set for this project or its parent ${project.parent}")
        }

        pom.description.set(project.description
          ?: "Instrumentation of Java libraries using OpenTelemetry.")
      }

      pom {
        name.set("OpenTelemetry Instrumentation for Java")
        url.set("https://github.com/open-telemetry/opentelemetry-java-instrumentation")

        licenses {
          license {
            name.set("The Apache License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
          }
        }

        developers {
          developer {
            id.set("opentelemetry")
            name.set("OpenTelemetry")
            url.set("https://github.com/open-telemetry/opentelemetry-java-instrumentation/discussions")
          }
        }

        scm {
          connection.set("scm:git:git@github.com:open-telemetry/opentelemetry-java-instrumentation.git")
          developerConnection.set("scm:git:git@github.com:open-telemetry/opentelemetry-java-instrumentation.git")
          url.set("git@github.com:open-telemetry/opentelemetry-java-instrumentation.git")
        }
      }
    }
  }
}

fun artifactPrefix(p: Project, archivesBaseName: String): String {
  if (archivesBaseName.startsWith("opentelemetry")) {
    return ""
  }
  if (p.name.startsWith("opentelemetry")) {
    return ""
  }
  if (p.name.startsWith("javaagent")) {
    return "opentelemetry-"
  }
  if (p.group == "io.opentelemetry.javaagent.instrumentation") {
    return "opentelemetry-javaagent-"
  }
  return "opentelemetry-"
}

// Sign only if we have a key to do so
val signingKey: String? = System.getenv("GPG_PRIVATE_KEY")
// Stub out entire signing block off of CI since Gradle provides no way of lazy configuration of
// signing tasks.
if (System.getenv("CI") != null && signingKey != null) {
  signing {
    useInMemoryPgpKeys(signingKey, System.getenv("GPG_PASSWORD"))
    sign(publishing.publications["maven"])
  }
}
