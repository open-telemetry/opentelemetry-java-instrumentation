plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")
  api("org.testcontainers:testcontainers")
  api("org.opensearch:opensearch-testcontainers:2.0.0")

  implementation("org.opensearch.client:opensearch-rest-client:1.0.0")
}
