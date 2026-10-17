plugins {
  id("otel.javaagent-testing")
}

dependencies {
  library("org.apache.wicket:wicket:8.0.0")

  testImplementation(project(":instrumentation:wicket-8.0:common-testing"))
  testImplementation("org.eclipse.jetty:jetty-server:8.0.0.v20110901")
  testImplementation("org.eclipse.jetty:jetty-servlet:8.0.0.v20110901")

  testInstrumentation(project(":instrumentation:wicket-8.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))

  latestDepTestLibrary("org.apache.wicket:wicket:9.+") // see wicket10-testing module
}

tasks {
  val testExceptionSignalLogs = register<Test>("testExceptionSignalLogs") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*WicketTest.testException")
    }
    jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
    systemProperty("metadataConfig", "otel.semconv.exception.signal.preview=logs")
  }

  val testExceptionSignalLogsDup = register<Test>("testExceptionSignalLogsDup") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*WicketTest.testException")
    }
    jvmArgs("-Dotel.semconv.exception.signal.preview=logs/dup")
    systemProperty("metadataConfig", "otel.semconv.exception.signal.preview=logs/dup")
  }

  check {
    dependsOn(testExceptionSignalLogs, testExceptionSignalLogsDup)
  }
}

// Wicket 9 requires Java 11
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
