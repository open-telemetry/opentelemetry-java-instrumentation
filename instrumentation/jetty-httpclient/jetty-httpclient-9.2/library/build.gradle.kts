plugins {
  id("otel.library-instrumentation")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.jetty.httpclient.v9_2")

// Jetty client 9.2 is the best starting point, HttpClient.send() is stable there
val jettyVers_base9 = "9.2.0.v20140526"

dependencies {
  library("org.eclipse.jetty:jetty-client:$jettyVers_base9")

  testImplementation(project(":instrumentation:jetty-httpclient::jetty-httpclient-9.2:testing"))

  latestDepTestLibrary("org.eclipse.jetty:jetty-client:9.+")
}
