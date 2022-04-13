import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  java

  id("com.google.cloud.tools.jib") version "3.2.1"
  id("com.diffplug.spotless") version "6.4.2"
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
  implementation(platform("io.grpc:grpc-bom:1.45.1"))
  implementation(platform("io.opentelemetry:opentelemetry-bom:1.0.0"))
  implementation(platform("io.opentelemetry:opentelemetry-bom-alpha:1.0.0-alpha"))
  implementation(platform("org.apache.logging.log4j:log4j-bom:2.17.2"))

  implementation("io.grpc:grpc-netty-shaded")
  implementation("io.grpc:grpc-protobuf")
  implementation("io.grpc:grpc-stub")
  implementation("io.opentelemetry.proto:opentelemetry-proto:0.16.0-alpha")
  implementation("io.opentelemetry:opentelemetry-extension-annotations")
  implementation("org.apache.logging.log4j:log4j-core")

  runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl")
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
  to.image = "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-grpc:jdk$targetJDK-$tag"
}
