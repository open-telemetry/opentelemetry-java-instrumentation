plugins {
  id("otel.library-instrumentation")
}

// Jetty client 9.2 is the best starting point, HttpClient.send() is stable there
val jettyVers_base9 = "9.2.0.v20140526"

dependencies {
  library("org.eclipse.jetty:jetty-client:$jettyVers_base9")
  latestDepTestLibrary("org.eclipse.jetty:jetty-client:9.+")
  testImplementation(project(":instrumentation:jetty-httpclient::jetty-httpclient-9.2:testing"))

  implementation("org.slf4j:slf4j-api")
}
