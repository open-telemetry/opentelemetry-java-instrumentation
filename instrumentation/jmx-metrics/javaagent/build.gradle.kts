plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:jmx-metrics:jmx-engine:library"))
  implementation(project(":instrumentation:jmx-metrics:jmx-yaml:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
}
