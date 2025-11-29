plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("io.opentelemetry.semconv:opentelemetry-semconv-incubating")
}
