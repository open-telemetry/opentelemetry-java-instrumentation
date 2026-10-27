plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.instrumentation:opentelemetry-instrumentation-testing")

  compileOnly("com.datastax.oss:java-driver-core:4.0.0")
  implementation("org.testcontainers:testcontainers")
}
