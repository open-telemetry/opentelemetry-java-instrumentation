plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("org.opensearch.client:opensearch-rest-client:1.3.6")
  compileOnly("com.google.auto.value:auto-value-annotations")

  annotationProcessor("com.google.auto.value:auto-value")
}

tasks {
  val testExceptionSignalLogs by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv.exception.signal.opt-in=logs")
  }

  check {
    dependsOn(testExceptionSignalLogs)
  }
}
