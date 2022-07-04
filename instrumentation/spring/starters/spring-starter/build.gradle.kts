plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

val versions: Map<String, String> by project

dependencies {
  api("org.springframework.boot:spring-boot-starter:${versions["org.springframework.boot"]}")
  api("org.springframework.boot:spring-boot-starter-aop:${versions["org.springframework.boot"]}")
  api(project(":instrumentation:spring:spring-boot-autoconfigure"))
  api("io.opentelemetry:opentelemetry-extension-annotations")
  api("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  api("io.opentelemetry:opentelemetry-api")
  api("io.opentelemetry:opentelemetry-exporter-logging")
  api("io.opentelemetry:opentelemetry-sdk")
}
