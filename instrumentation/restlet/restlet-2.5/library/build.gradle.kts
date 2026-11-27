plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.restlet:org.restlet:2.5.0")

  testImplementation(project(":instrumentation:restlet:restlet-2.5:testing"))
  testLibrary("org.restlet:org.restlet.ext.spring:2.5.0")
  if (!otelProps.testLatestDeps) {
    // Restlet 2.7 includes the Jetty connector in core.
    testImplementation("org.restlet:org.restlet.ext.jetty:2.5.0")
  }
  latestDepTestLibrary("org.restlet:org.restlet:2.+")
  latestDepTestLibrary("org.restlet:org.restlet.ext.spring:2.+")
}
