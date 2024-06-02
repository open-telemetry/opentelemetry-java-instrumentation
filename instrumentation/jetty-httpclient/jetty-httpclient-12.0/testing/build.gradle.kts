plugins {
  id("otel.java-conventions")
}

// We do not want to support alpha or beta version
val jettyVers_base12 = "12.0.0"

dependencies {
  api(project(":testing-common"))

  api("org.eclipse.jetty:jetty-client:$jettyVers_base12")

  implementation("io.opentelemetry:opentelemetry-api")
}
