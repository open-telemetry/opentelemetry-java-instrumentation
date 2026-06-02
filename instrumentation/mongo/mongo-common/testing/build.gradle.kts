plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")

  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.testcontainers:testcontainers")
}
