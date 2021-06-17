import com.github.jengelman.gradle.plugins.shadow.ShadowExtension

plugins {
  `maven-publish`
  signing
}

publishing {
  publications {
    register<MavenPublication>("maven") {
      if (tasks.names.contains("shadowJar") && findProperty("noShadowPublish") != true) {
        the<ShadowExtension>().component(this)
        // These two are here just to satisfy Maven Central
        artifact(tasks["sourcesJar"])
        artifact(tasks["javadocJar"])
      } else {
        plugins.withId("java-platform") {
          from(components["javaPlatform"])
        }
        plugins.withId("java-library") {
          from(components["java"])
        }
      }

      versionMapping {
        allVariants {
          fromResolutionResult()
        }
      }

      if (findProperty("otel.stable") != "true") {
        val versionParts = version.split('-').toMutableList()
        versionParts[0] += "-alpha"
        version = versionParts.joinToString("-")
      }

      afterEvaluate {
        val mavenGroupId: String? by project
        if (mavenGroupId != null) {
          groupId = mavenGroupId
        }
        artifactId = artifactPrefix(project, base.archivesBaseName) + base.archivesBaseName

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
