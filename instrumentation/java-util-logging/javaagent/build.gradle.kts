plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":instrumentation:java-util-logging:shaded-stub-for-instrumenting"))

  compileOnly("io.opentelemetry:opentelemetry-api-logs")
  compileOnly(project(":javaagent-bootstrap"))

  // ensure no cross interference
  testInstrumentation(project(":instrumentation:jboss-logmanager:jboss-logmanager-appender-1.1:javaagent"))

  testImplementation("org.awaitility:awaitility")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.java-util-logging.experimental-log-attributes=true")
}
