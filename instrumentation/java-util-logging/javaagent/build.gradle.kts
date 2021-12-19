plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":instrumentation:java-util-logging:shaded-stub-for-instrumenting"))

  compileOnly(project(":instrumentation-api-appender"))

  testLibrary("org.jboss.logmanager:jboss-logmanager:1.0.0.GA")
  testImplementation("org.awaitility:awaitility")
}
