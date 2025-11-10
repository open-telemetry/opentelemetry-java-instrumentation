plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")
  api("org.testcontainers:testcontainers-mongodb")

  implementation("io.opentelemetry:opentelemetry-api")
}
