plugins {
  id("com.github.johnrengelman.shadow")
  id("otel.java-conventions")
}

tasks.jar {
  enabled = false
}

dependencies {
  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service")

  compileOnly(project(":javaagent-extension-api"))
  compileOnly(project(":javaagent-instrumentation-api"))
  compileOnly(project(":javaagent-tooling"))

  implementation("io.grpc:grpc-core:1.33.1")
  implementation("io.opentelemetry:opentelemetry-exporter-otlp")
  implementation("io.opentelemetry:opentelemetry-exporter-otlp-metrics")
  implementation("io.opentelemetry:opentelemetry-proto")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  compileOnly("org.slf4j:slf4j-api")
}
