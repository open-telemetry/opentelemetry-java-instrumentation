plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:runtime-metrics-jfr:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
}
