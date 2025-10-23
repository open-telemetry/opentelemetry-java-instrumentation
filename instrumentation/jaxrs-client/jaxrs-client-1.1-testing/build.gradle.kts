plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  testLibrary("com.sun.jersey:jersey-client:1.1.4")

  testInstrumentation(project(":instrumentation:http-url-connection:javaagent"))
}
