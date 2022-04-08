plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("org.apache.httpcomponents:httpclient:4.3")

  implementation("io.opentelemetry:opentelemetry-api")
}
