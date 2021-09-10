// Project to collect and shade exporter dependencies included in the agent's full distribution.

plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("io.opentelemetry:opentelemetry-exporter-jaeger")
  implementation("io.opentelemetry:opentelemetry-exporter-otlp")
  implementation("io.opentelemetry:opentelemetry-exporter-otlp-metrics")
  implementation("io.opentelemetry:opentelemetry-exporter-otlp-http-trace")
  implementation("io.opentelemetry:opentelemetry-exporter-otlp-http-metrics")
  implementation("io.opentelemetry:opentelemetry-exporter-logging-otlp")

  implementation("io.opentelemetry:opentelemetry-exporter-prometheus")
  implementation("io.prometheus:simpleclient")
  implementation("io.prometheus:simpleclient_httpserver")

  implementation("io.opentelemetry:opentelemetry-exporter-zipkin")

  // TODO(anuraaga): Move version to dependency management
  implementation("io.grpc:grpc-netty-shaded:1.38.0")
}
