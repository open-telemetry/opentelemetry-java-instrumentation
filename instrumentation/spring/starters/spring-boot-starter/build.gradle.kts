plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
  id("otel.japicmp-conventions")
}

group = "io.opentelemetry.instrumentation"

val springBootVersion = "2.6.15"

dependencies {
  compileOnly("org.springframework.boot:spring-boot-starter:$springBootVersion")
  compileOnly("org.springframework.boot:spring-boot-starter-aop:$springBootVersion")
  api(project(":instrumentation:spring:spring-boot-autoconfigure"))
  api(project(":instrumentation-annotations"))
  implementation(project(":instrumentation:resources:library"))
  implementation("io.opentelemetry:opentelemetry-sdk-extension-incubator")
  api("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  api("io.opentelemetry:opentelemetry-api")
  api("io.opentelemetry:opentelemetry-exporter-logging")
  api("io.opentelemetry:opentelemetry-exporter-otlp")
  api("io.opentelemetry:opentelemetry-sdk")

  implementation("io.opentelemetry.contrib:opentelemetry-aws-resources")
  implementation("io.opentelemetry.contrib:opentelemetry-gcp-resources")
  implementation("io.opentelemetry.contrib:opentelemetry-baggage-processor")
}
