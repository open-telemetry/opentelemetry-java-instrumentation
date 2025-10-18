plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  testLibrary("com.sun.jersey:jersey-client:1.1.4")

  testInstrumentation(project(":instrumentation:http-url-connection:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("otel.instrumentation.common.peer-service-mapping", "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service")
  }
}
