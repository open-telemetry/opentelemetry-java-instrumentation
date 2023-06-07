plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:runtime-metrics:runtime-metrics-java8:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
}
