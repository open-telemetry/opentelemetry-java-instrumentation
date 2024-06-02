plugins {
  id("otel.library-instrumentation")
}

// We do not want to support alpha or beta version
val jettyVers_base12 = "12.0.0"

dependencies {
  library("org.eclipse.jetty:jetty-client:$jettyVers_base12")

  testImplementation(project(":instrumentation:jetty-httpclient::jetty-httpclient-12.0:testing"))

  latestDepTestLibrary("org.eclipse.jetty:jetty-client:12.+")
}
