plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("org.eclipse.jetty:jetty-client:12.0.0")

  implementation("io.opentelemetry:opentelemetry-api")
}
