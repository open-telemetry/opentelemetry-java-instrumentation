plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencies {
  api(project(":instrumentation:spring:starters:spring-boot-starter"))
  api("io.opentelemetry:opentelemetry-exporter-zipkin")
}
