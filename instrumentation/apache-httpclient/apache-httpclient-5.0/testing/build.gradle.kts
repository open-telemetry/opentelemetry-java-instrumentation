plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("org.apache.httpcomponents.client5:httpclient5:5.2.1")

  implementation("io.opentelemetry:opentelemetry-api")
}
