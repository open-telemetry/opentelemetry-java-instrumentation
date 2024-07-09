plugins {
  id("io.spring.dependency-management") version "1.1.6"
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:2.6.15")
  }
}

dependencies {
  api("org.springframework.boot:spring-boot-starter")
  api("org.springframework.boot:spring-boot-starter-aop")
  api(project(":instrumentation:spring:spring-boot-autoconfigure"))
  api(project(":instrumentation:spring:spring-boot-autoconfigure-3"))
  api(project(":instrumentation-annotations"))
  implementation(project(":instrumentation:resources:library"))
  implementation("io.opentelemetry:opentelemetry-sdk-extension-incubator")
  api("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  api("io.opentelemetry:opentelemetry-api")
  api("io.opentelemetry:opentelemetry-exporter-logging")
  api("io.opentelemetry:opentelemetry-exporter-otlp")
  api("io.opentelemetry:opentelemetry-sdk")

  implementation("io.opentelemetry.contrib:opentelemetry-aws-resources")
  implementation("io.opentelemetry.contrib:opentelemetry-gcp-resources")
}
