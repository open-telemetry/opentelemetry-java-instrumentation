plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.apache.logging.log4j:log4j-core:2.17.0")
  annotationProcessor("org.apache.logging.log4j:log4j-core:2.17.0")

  // to be removed in 3.0
  implementation(project(":instrumentation:log4j:log4j-context-data:log4j-context-data-2.17:library-autoconfigure"))

  testImplementation(project(":instrumentation:log4j:log4j-appender-2.17:testing"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testLibrary("com.lmax:disruptor:3.3.4")

  if (otelProps.testLatestDeps) {
    testCompileOnly("biz.aQute.bnd:biz.aQute.bnd.annotation:7.0.0")
    testCompileOnly("com.google.errorprone:error_prone_annotations")
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  val testAsyncLogger = register<Test>("testAsyncLogger") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs(
      "-DLog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector",
      "-Dlog4j2.ContextDataInjector=io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppenderContextDataInjector",
    )
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=code")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=code/dup")
  }

  check {
    dependsOn(testAsyncLogger, testStableSemconv, testBothSemconv)
  }
}
