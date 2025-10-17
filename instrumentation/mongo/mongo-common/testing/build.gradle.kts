plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  api("org.testcontainers:testcontainers-mongodb")

  implementation("io.opentelemetry:opentelemetry-api")
}
