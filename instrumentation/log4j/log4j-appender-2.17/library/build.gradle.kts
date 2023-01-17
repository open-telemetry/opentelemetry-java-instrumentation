plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("io.opentelemetry:opentelemetry-api-logs")

  library("org.apache.logging.log4j:log4j-core:2.17.0")

  testImplementation("io.opentelemetry:opentelemetry-sdk-logs")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("io.opentelemetry:opentelemetry-sdk-logs-testing")
}