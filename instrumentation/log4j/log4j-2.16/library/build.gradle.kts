plugins {
  id("otel.library-instrumentation")
}

dependencies {
  api(project(":instrumentation-api-appender"))

  library("org.apache.logging.log4j:log4j-core:2.16.0")

  testImplementation("io.opentelemetry:opentelemetry-sdk-logs")

  testImplementation("org.mockito:mockito-core")
}
