plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  api("io.opentelemetry:opentelemetry-opencensus-shim")

  api(project(":testing-common"))
}
