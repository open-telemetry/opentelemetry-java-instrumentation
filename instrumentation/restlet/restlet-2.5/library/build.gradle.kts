plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.restlet:org.restlet:2.5.0")

  testImplementation(project(":instrumentation:restlet:restlet-2.5:testing"))
  testLibrary("org.restlet:org.restlet.ext.spring:2.5.0")
  if (!otelProps.testLatestDeps) {
    // The JDK connector in Restlet 2.5 times out in the shared high-concurrency server tests.
    testRuntimeOnly("org.restlet:org.restlet.ext.jetty:2.5.0")
  }
}
