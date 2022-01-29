plugins {
  id("otel.java-conventions")
  id("otel.animalsniffer-conventions")
  id("otel.jacoco-conventions")
  id("otel.japicmp-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencies {
  api(project(":instrumentation-appender-api-internal"))

  api("io.opentelemetry:opentelemetry-sdk-logs")

  annotationProcessor(project(":custom-checks"))
}
