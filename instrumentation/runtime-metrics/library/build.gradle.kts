plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation-api"))

  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics")
  testImplementation(project(":testing-common"))
}
