plugins {
  id("otel.library-instrumentation")
}

dependencies {
  api(project(":instrumentation-api-appender"))

  library("org.apache.logging.log4j:log4j-core:2.16.0")

  testImplementation(project(":instrumentation-sdk-appender"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-logs")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")

  testImplementation("org.mockito:mockito-core")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-map-message-attributes=true")
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-context-data-attributes=*")
}
