plugins {
  id("otel.javaagent-testing")
}

dependencies {
  library("org.eclipse.jetty.ee10:jetty-ee10-servlet:12.0.6")

  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-11.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-12.0:javaagent"))

  testImplementation(project(":instrumentation:servlet:servlet-5.0:testing"))
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  test {
    jvmArgs("-Dotel.instrumentation.servlet.experimental.capture-request-parameters=test-parameter,Test-Parameter")
    systemProperty("collectMetadata", otelProps.collectMetadata)
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.servlet.experimental.capture-request-parameters=test-parameter,Test-Parameter",
    )
  }
}
