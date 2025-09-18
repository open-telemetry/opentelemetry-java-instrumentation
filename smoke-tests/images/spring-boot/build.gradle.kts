import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  id("otel.java-conventions")

  id("com.google.cloud.tools.jib")
  id("org.springframework.boot") version "3.5.6"
}

dependencies {
  implementation(platform("io.opentelemetry:opentelemetry-bom"))
  implementation(platform("org.springframework.boot:spring-boot-dependencies:2.6.15"))

  implementation("io.opentelemetry:opentelemetry-api")
  implementation(project(":instrumentation-annotations"))
  implementation("org.springframework.boot:spring-boot-starter-web")
}

otelJava {
  minJavaVersionSupported = JavaVersion.VERSION_17
}

configurations.runtimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.13")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}

val targetJDK = project.findProperty("targetJDK") ?: "17"

val tag = findProperty("tag")
  ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

springBoot {
  buildInfo {
  }
}

val repo = System.getenv("GITHUB_REPOSITORY") ?: "open-telemetry/opentelemetry-java-instrumentation"

jib {
  from.image = "openjdk:$targetJDK"
  to.image = "ghcr.io/$repo/smoke-test-spring-boot:jdk$targetJDK-$tag"
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
