import org.gradle.kotlin.dsl.testImplementation

plugins {
  id("otel.library-instrumentation")
  id("otel.java-conventions")
}

val springBootVersion = "2.7.18"

dependencies {
  compileOnly("org.springframework.boot:spring-boot-restclient:4.0.0")
  implementation("org.springframework.kafka:spring-kafka:2.9.0")

  library("org.springframework.boot:spring-boot-starter-test:$springBootVersion") {
    exclude("org.junit.vintage", "junit-vintage-engine")
  }
  library("org.springframework.boot:spring-boot-starter-data-r2dbc:$springBootVersion")

  implementation(project(":instrumentation:micrometer:micrometer-1.5:library"))
  implementation(project(":instrumentation:spring:spring-boot-autoconfigure"))
  implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  implementation("io.opentelemetry:opentelemetry-sdk")
  implementation("io.opentelemetry:opentelemetry-sdk-testing")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
}
