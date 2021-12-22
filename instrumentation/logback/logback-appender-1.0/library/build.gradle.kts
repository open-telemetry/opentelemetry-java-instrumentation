plugins {
  id("otel.library-instrumentation")
}

dependencies {
  api(project(":instrumentation-api-appender"))

  library("ch.qos.logback:logback-classic:0.9.16")

  testImplementation(project(":instrumentation-sdk-appender"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-logs")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")

  testImplementation("org.mockito:mockito-core")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-mdc-attributes=*")
}
