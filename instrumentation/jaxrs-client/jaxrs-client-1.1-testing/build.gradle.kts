plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testLibrary("com.sun.jersey:jersey-client:1.1.4")

  testInstrumentation(project(":instrumentation:http-url-connection:javaagent"))
}
