import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  java

  id("com.diffplug.spotless") version "6.4.2"
  id("com.google.cloud.tools.jib") version "3.2.1"
}

group = "io.opentelemetry"
version = "0.0.1-SNAPSHOT"

repositories {
  mavenCentral()
}

spotless {
  java {
    googleJavaFormat()
    licenseHeaderFile(rootProject.file("../../../buildscripts/spotless.license.java"), "(package|import|public)")
  }
  kotlinGradle {
    ktlint().userData(mapOf("indent_size" to "2", "continuation_indent_size" to "2", "disabled_rules" to "no-wildcard-imports"))
    target("**/*.gradle.kts")
  }
}

dependencies {
  implementation(platform("io.opentelemetry:opentelemetry-bom:1.0.0"))
  implementation(platform("org.springframework.boot:spring-boot-dependencies:2.6.6"))

  implementation("io.opentelemetry:opentelemetry-api")
  implementation("io.opentelemetry:opentelemetry-extension-annotations")
  implementation("org.springframework.boot:spring-boot-starter-web")
}

tasks {
  withType<JavaCompile>().configureEach {
    with(options) {
      release.set(8)
    }
  }
}

val targetJDK = project.findProperty("targetJDK") ?: "11"

val tag = findProperty("tag") ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

jib {
  from.image = "openjdk:$targetJDK"
  to.image = "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk$targetJDK-$tag"
  container.ports = listOf("8080")
}
