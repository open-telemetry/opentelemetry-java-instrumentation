plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("io.opentelemetry:opentelemetry-api-metrics")

  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics")
  testImplementation(project(":testing-common"))
  testImplementation("org.mockito:mockito-core")
}
