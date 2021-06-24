plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = 'io.opentelemetry.instrumentation'

dependencies {
  api group: "org.springframework.boot", name: "spring-boot-starter", version: versions["org.springframework.boot"]
  api project(':instrumentation:spring:starters:spring-starter')
  api "io.opentelemetry:opentelemetry-exporter-otlp"
  implementation "io.grpc:grpc-netty-shaded:1.30.2"
}

