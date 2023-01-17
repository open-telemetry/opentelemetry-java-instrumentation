plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:jmx-metrics:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
}