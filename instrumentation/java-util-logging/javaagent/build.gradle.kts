plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":instrumentation:java-util-logging:shaded-stub-for-instrumenting"))

  compileOnly(project(":javaagent-bootstrap"))

  // ensure no cross interference
  testInstrumentation(project(":instrumentation:jboss-logmanager:jboss-logmanager-appender-1.1:javaagent"))

  testImplementation("org.awaitility:awaitility")
}

tasks {
  val testExperimental by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.java-util-logging.experimental-log-attributes=true")
  }

  check {
    dependsOn(testExperimental)
  }
}
