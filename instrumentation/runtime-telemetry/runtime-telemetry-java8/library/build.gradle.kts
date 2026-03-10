plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:runtime-telemetry:library"))
}
