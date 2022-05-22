import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  id("otel.java-conventions")

  id("com.google.cloud.tools.jib")
}

dependencies {
  implementation(platform("io.opentelemetry:opentelemetry-bom:1.0.0"))
  implementation(platform("org.springframework.boot:spring-boot-dependencies:2.6.6"))

  implementation("io.opentelemetry:opentelemetry-api")
  implementation("io.opentelemetry:opentelemetry-extension-annotations")
  implementation("org.springframework.boot:spring-boot-starter-web")
}

val targetJDK = project.findProperty("targetJDK") ?: "11"

val tag = findProperty("tag") ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

jib {
  from.image = "openjdk:$targetJDK"
  to.image = "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk$targetJDK-$tag"
  container.ports = listOf("8080")
}
