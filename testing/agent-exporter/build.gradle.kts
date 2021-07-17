plugins {
  id("otel.java-conventions")
}

dependencies {
  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service")

  implementation(project(":javaagent-extension-api"))
  implementation(project(":javaagent-instrumentation-api"))
  implementation(project(":javaagent-tooling"))

  implementation("io.grpc:grpc-core:1.33.1")
  implementation("io.opentelemetry:opentelemetry-exporter-otlp")
  implementation("io.opentelemetry:opentelemetry-exporter-otlp-metrics")
  implementation("io.opentelemetry:opentelemetry-proto")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  implementation("org.slf4j:slf4j-api")
}
