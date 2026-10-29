plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing")

  api("org.eclipse.jetty:jetty-client:12.0.0")

  implementation("io.opentelemetry:opentelemetry-api")
}
