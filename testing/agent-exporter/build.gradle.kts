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

  compileOnly(project(":instrumentation-api"))
  compileOnly(project(":javaagent-extension-api"))
  compileOnly(project(":javaagent-bootstrap"))
  compileOnly(project(":javaagent-tooling"))

  compileOnly(project(":instrumentation-appender-api-internal"))
  compileOnly(project(":instrumentation-appender-sdk-internal"))

  implementation("io.opentelemetry:opentelemetry-exporter-otlp-common")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
}
