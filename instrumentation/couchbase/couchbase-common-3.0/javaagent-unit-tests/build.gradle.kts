plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:couchbase:couchbase-common-3.0:javaagent"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":javaagent-extension-api"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}

tasks {
  val testExceptionSignalLogs = register<Test>("testExceptionSignalLogs") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
  }

  val testExceptionSignalLogsDup = register<Test>("testExceptionSignalLogsDup") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv.exception.signal.preview=logs/dup")
  }

  check {
    dependsOn(testExceptionSignalLogs, testExceptionSignalLogsDup)
  }
}
