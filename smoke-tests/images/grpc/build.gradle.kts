import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  id("otel.java-conventions")

  id("com.google.cloud.tools.jib")
}

dependencies {
  implementation(platform("io.grpc:grpc-bom:1.73.0"))
  implementation(platform("io.opentelemetry:opentelemetry-bom:1.0.0"))
  implementation(platform("io.opentelemetry:opentelemetry-bom-alpha:1.0.0-alpha"))
  implementation(platform("org.apache.logging.log4j:log4j-bom:2.25.1"))

  implementation("io.grpc:grpc-netty-shaded")
  implementation("io.grpc:grpc-protobuf")
  implementation("io.grpc:grpc-stub")
  implementation("io.opentelemetry.proto:opentelemetry-proto")
  implementation(project(":instrumentation-annotations"))
  implementation("org.apache.logging.log4j:log4j-core")

  runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl")
}

val targetJDK = project.findProperty("targetJDK") ?: "11"

val tag = findProperty("tag")
  ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

java {
  // this is needed to avoid jib failing with
  // "Your project is using Java 17 but the base image is for Java 8"
  // (it seems the jib plugins does not understand toolchains yet)
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

val repo = System.getenv("GITHUB_REPOSITORY") ?: "open-telemetry/opentelemetry-java-instrumentation"

jib {
  from.image = "eclipse-temurin:$targetJDK"
  to.image = "ghcr.io/$repo/smoke-test-grpc:jdk$targetJDK-$tag"
}
