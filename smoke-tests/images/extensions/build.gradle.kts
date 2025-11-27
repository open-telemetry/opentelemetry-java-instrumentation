import com.google.cloud.tools.jib.gradle.JibTask
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  id("otel.java-conventions")
  id("com.google.cloud.tools.jib")
}

dependencies {
}

val targetJDK = project.findProperty("targetJDK") ?: "17"

val tag = findProperty("tag")
  ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

java {
  // needed by jib to detect java version used in project
  // for jdk9+ jib uses an entrypoint that doesn't work with jdk8
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

val repo = System.getenv("GITHUB_REPOSITORY") ?: "open-telemetry/opentelemetry-java-instrumentation"

jib {
  from.image = "eclipse-temurin:$targetJDK"
  to.image = "ghcr.io/$repo/smoke-test-extensions:jdk$targetJDK-$tag"
}

tasks {
  withType<JibTask>().configureEach {
    // Jib tasks access Task.project at execution time which is not compatible with configuration cache
    notCompatibleWithConfigurationCache("Jib task accesses Task.project at execution time")
  }
}
