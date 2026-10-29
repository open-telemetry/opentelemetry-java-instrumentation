plugins {
  id("otel.java-conventions")
}

// Jetty client 9.2 is the best starting point, HttpClient.send() is stable there
val jettyVersBase9 = "9.2.0.v20140526"

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing")

  api("org.eclipse.jetty:jetty-client:$jettyVersBase9")

  implementation("io.opentelemetry:opentelemetry-api")
}
