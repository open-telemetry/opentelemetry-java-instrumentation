plugins {
  id("otel.javaagent-testing")
}

// separate testing module is needed, because presence of jboss-logmanager 2.1.6 or later
// on the classpath causes the normal java.util.logging test to use it

dependencies {
  compileOnly(project(":instrumentation:java-util-logging:shaded-stub-for-instrumenting"))

  compileOnly(project(":instrumentation-appender-api-internal"))

  testInstrumentation(project(":instrumentation:java-util-logging:javaagent"))

  // the JBoss instrumentation in this artifact is needed
  // for jboss-logmanager versions 1.1.0.GA through latest 2.x
  testLibrary("org.jboss.logmanager:jboss-logmanager:1.1.0.GA")

  testImplementation("org.awaitility:awaitility")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.java-util-logging.experimental-log-attributes=true")
}
