plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
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
  }

  val testCaptureTemplateAndArguments by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs(
      "-Dotel.instrumentation.java-util-logging.experimental.capture-template=true",
      "-Dotel.instrumentation.java-util-logging.experimental.capture-arguments=true",
    )
  }

  check {
    dependsOn(testExperimental)
    dependsOn(testCaptureTemplateAndArguments)
  }
}
