plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation-appender-api-internal"))
  implementation(project(":instrumentation-appender-sdk-internal"))

  library("org.apache.logging.log4j:log4j-core:2.17.0")

  testImplementation(project(":instrumentation-appender-sdk-internal"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-logs")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}
