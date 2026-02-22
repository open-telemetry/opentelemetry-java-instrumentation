plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("io.vertx:vertx-sql-client:4.0.0")
  compileOnly("io.vertx:vertx-codegen:4.0.0")
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
