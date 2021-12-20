plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":instrumentation:java-util-logging:shaded-stub-for-instrumenting"))

  compileOnly(project(":instrumentation-api-appender"))

  testImplementation("org.awaitility:awaitility")
}
