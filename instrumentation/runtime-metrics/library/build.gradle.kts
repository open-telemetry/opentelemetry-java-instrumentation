plugins {
  id("otel.library-instrumentation")
}

dependencies {
  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics")
  testImplementation(project(":testing-common"))
  testImplementation("org.mockito:mockito-core")
}
