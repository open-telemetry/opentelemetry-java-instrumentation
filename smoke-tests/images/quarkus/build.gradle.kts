import com.google.cloud.tools.jib.gradle.JibTask
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

buildscript {
  dependencies {
    classpath("com.google.cloud.tools:jib-quarkus-extension-gradle:0.1.1")
  }
}

plugins {
  id("otel.java-conventions")

  id("com.google.cloud.tools.jib")
  id("io.quarkus") version "2.8.0.Final"
}

dependencies {
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:2.8.0.Final"))
  implementation("io.quarkus:quarkus-resteasy")
}

quarkus {
  // Expected by jib extension.
  // TODO(anuraaga): Switch to quarkus plugin native jib support.
  setFinalName("opentelemetry-quarkus-$version")
}

val targetJDK = project.findProperty("targetJDK") ?: "11"

val tag = findProperty("tag") ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

jib {
  from.image = "openjdk:$targetJDK"
  to.image = "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-quarkus:jdk$targetJDK-$tag"
  container {
    mainClass = "bogus" // to suppress Jib warning about missing main class
  }
  pluginExtensions {
    pluginExtension {
      implementation = "com.google.cloud.tools.jib.gradle.extension.quarkus.JibQuarkusExtension"
    }
  }
}

tasks {
  withType<JavaCompile>().configureEach {
    with(options) {
      // Quarkus 2.0+ does not support Java 8
      release.set(11)
    }
  }

  withType<JibTask>().configureEach {
    dependsOn(quarkusBuild)
  }
}
