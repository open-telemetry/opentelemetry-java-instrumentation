import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  id("otel.java-conventions")

  id("com.google.cloud.tools.jib")
  id("org.springframework.boot") version "2.6.6"
}

dependencies {
  implementation(platform("io.opentelemetry:opentelemetry-bom:1.0.0"))
  implementation(platform("org.springframework.boot:spring-boot-dependencies:2.6.6"))

  implementation("io.opentelemetry:opentelemetry-api")
  implementation(project(":instrumentation-annotations"))
  implementation("org.springframework.boot:spring-boot-starter-web")
}

configurations.runtimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:2.0.7")
  }
}

val targetJDK = project.findProperty("targetJDK") ?: "11"

val tag = findProperty("tag")
  ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

jib {
  from.image = "openjdk:$targetJDK"
  to.image = "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk$targetJDK-$tag"
  container.ports = listOf("8080")
}

tasks {
  val springBootJar by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
  }

  artifacts {
    add("springBootJar", bootJar)
  }
}
