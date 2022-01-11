plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":instrumentation:java-util-logging:shaded-stub-for-instrumenting"))

  compileOnly(project(":instrumentation-appender-api-internal"))

  testImplementation("org.awaitility:awaitility")
}
