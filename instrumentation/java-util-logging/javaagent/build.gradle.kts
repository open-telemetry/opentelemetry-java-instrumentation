plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":instrumentation:java-util-logging:shaded-stub-for-instrumenting"))

  compileOnly(project(":instrumentation-appender-api-internal"))

  // ensure no cross interference
  testInstrumentation(project(":instrumentation:jboss-logmanager-2.1:javaagent"))

  testImplementation("org.awaitility:awaitility")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.java-util-logging.experimental-log-attributes=true")
}
