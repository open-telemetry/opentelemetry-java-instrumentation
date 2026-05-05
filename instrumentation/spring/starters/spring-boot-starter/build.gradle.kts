plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencies {
  api(project(":instrumentation:spring:spring-boot-autoconfigure"))
  api(project(":instrumentation-annotations"))
  api("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  api("io.opentelemetry:opentelemetry-api")
  api("io.opentelemetry:opentelemetry-exporter-logging")
  api("io.opentelemetry:opentelemetry-exporter-otlp")
  api("io.opentelemetry:opentelemetry-sdk")

  implementation(project(":instrumentation:resources:library"))
  implementation("io.opentelemetry:opentelemetry-sdk-extension-incubator")
  implementation("io.opentelemetry.contrib:opentelemetry-azure-resources")
  implementation("io.opentelemetry.contrib:opentelemetry-aws-resources")
  implementation("io.opentelemetry.contrib:opentelemetry-gcp-resources")
  implementation("io.opentelemetry.contrib:opentelemetry-cloudfoundry-resources")
  implementation("io.opentelemetry.contrib:opentelemetry-baggage-processor")
  implementation("io.opentelemetry.contrib:opentelemetry-samplers")
}
