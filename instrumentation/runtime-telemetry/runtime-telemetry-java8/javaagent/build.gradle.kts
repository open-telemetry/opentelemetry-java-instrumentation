plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:runtime-telemetry:runtime-telemetry-java8:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
}
