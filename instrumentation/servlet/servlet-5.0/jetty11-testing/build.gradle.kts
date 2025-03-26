plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-11.0:javaagent"))
  testImplementation(project(":instrumentation:servlet:servlet-5.0:testing"))

  testLibrary("org.eclipse.jetty:jetty-server:11.0.0")
  testLibrary("org.eclipse.jetty:jetty-servlet:11.0.0")

  latestDepTestLibrary("org.eclipse.jetty:jetty-server:11.+") // see jetty-12-testing module
}

// Jetty 11 requires Java 11
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.servlet.experimental.capture-request-parameters=test-parameter")
  }
}
