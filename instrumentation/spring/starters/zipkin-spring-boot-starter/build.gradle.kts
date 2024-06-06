plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

val versions: Map<String, String> by project
val springBootVersion = "2.6.15"

dependencies {
  api("org.springframework.boot:spring-boot-starter:$springBootVersion")
  api(project(":instrumentation:spring:starters:spring-boot-starter"))
  api("io.opentelemetry:opentelemetry-exporter-zipkin")
}
