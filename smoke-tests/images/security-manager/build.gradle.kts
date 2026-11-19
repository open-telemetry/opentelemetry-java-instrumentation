import com.google.cloud.tools.jib.gradle.JibTask
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
  id("otel.java-conventions")

  id("com.google.cloud.tools.jib")
}

dependencies {
  implementation(platform("io.opentelemetry:opentelemetry-bom:1.0.0"))

  implementation("io.opentelemetry:opentelemetry-api")
}

val targetJDK = project.findProperty("targetJDK") ?: "11"

val tag = findProperty("tag")
  ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

val runtimeImages = Properties().apply {
  rootProject.file("smoke-tests/images/java-runtime-images.properties").inputStream().use { load(it) }
}
val latestRuntimeImage = runtimeImages.getProperty("latest")
val fromImage = runtimeImages.getProperty(targetJDK.toString())
  ?: latestRuntimeImage?.takeIf {
    it.substringAfterLast(':').substringBefore('@').takeWhile(Char::isDigit) == targetJDK.toString()
  }
  ?: error("Unsupported targetJDK: $targetJDK")

java {
  // needed by jib to detect java version used in project
  // for jdk9+ jib uses an entrypoint that doesn't work with jdk8
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

val repo = System.getenv("GITHUB_REPOSITORY") ?: "open-telemetry/opentelemetry-java-instrumentation"

jib {
  from.image = fromImage
  to.image = "ghcr.io/$repo/smoke-test-security-manager:jdk$targetJDK-$tag"
  container.mainClass = "io.opentelemetry.smoketest.securitymanager.Main"
  container.jvmFlags = listOf("-Djava.security.manager", "-Djava.security.policy=/app/resources/security.policy")
}

tasks {
  withType<JibTask>().configureEach {
    // Jib tasks access Task.project at execution time which is not compatible with configuration cache
    notCompatibleWithConfigurationCache("Jib task accesses Task.project at execution time")
  }
}
