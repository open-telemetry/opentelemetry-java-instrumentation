plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":instrumentation:java-util-logging:shaded-stub-for-instrumenting"))

  // ensure no cross interference
  testInstrumentation(project(":instrumentation:jboss-logmanager:jboss-logmanager-appender-1.1:javaagent"))
}

tasks {
  val testExperimental by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.java-util-logging.experimental-log-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.java-util-logging.experimental-log-attributes=true")
  }

  check {
    dependsOn(testExperimental)
  }
}
