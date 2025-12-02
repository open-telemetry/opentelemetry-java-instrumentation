import org.gradle.kotlin.dsl.testImplementation

plugins {
  id("otel.library-instrumentation")
  id("otel.java-conventions")
}

val springBootVersion = "2.7.18"

dependencies {
  library("org.springframework.boot:spring-boot-starter-test:$springBootVersion") {
    exclude("org.junit.vintage", "junit-vintage-engine")
  }
  implementation(project(":instrumentation:micrometer:micrometer-1.5:library"))
  implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  implementation("io.opentelemetry:opentelemetry-sdk")
  implementation("io.opentelemetry:opentelemetry-sdk-testing")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
}
