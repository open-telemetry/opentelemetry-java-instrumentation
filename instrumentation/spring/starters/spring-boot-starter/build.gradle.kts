plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

val versions: Map<String, String> by project
val springBootVersion = versions["org.springframework.boot"]

dependencies {
  api("org.springframework.boot:spring-boot-starter:$springBootVersion")
  api("org.springframework.boot:spring-boot-starter-aop:$springBootVersion")
  api(project(":instrumentation:spring:spring-boot-autoconfigure"))
  api(project(":instrumentation-annotations"))
  implementation(project(":instrumentation:resources:library"))
  api("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  api("io.opentelemetry:opentelemetry-api")
  api("io.opentelemetry:opentelemetry-exporter-logging")
  api("io.opentelemetry:opentelemetry-exporter-otlp")
  api("io.opentelemetry:opentelemetry-sdk")
  api(project(":instrumentation-annotations"))

  implementation("io.opentelemetry.contrib:opentelemetry-aws-resources:1.33.0-alpha") {
    exclude("com.fasterxml.jackson.core", "jackson-core")
    exclude("com.squareup.okhttp3", "okhttp")
  }

  implementation("io.opentelemetry.contrib:opentelemetry-gcp-resources:1.33.0-alpha") {
    exclude("com.fasterxml.jackson.core", "jackson-core")
  }
}
