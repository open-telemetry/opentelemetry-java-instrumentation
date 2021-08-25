plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

val versions: Map<String, String> by project

dependencies {
  api("org.springframework.boot:spring-boot-starter:${versions["org.springframework.boot"]}")
  api(project(":instrumentation:spring:starters:spring-starter"))
  api("io.opentelemetry:opentelemetry-exporter-otlp")
  implementation("io.grpc:grpc-netty-shaded:1.30.2")
}
