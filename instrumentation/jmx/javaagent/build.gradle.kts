plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:jmx:jmx-engine:library"))
  implementation(project(":instrumentation:jmx:jmx-yaml:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
}
