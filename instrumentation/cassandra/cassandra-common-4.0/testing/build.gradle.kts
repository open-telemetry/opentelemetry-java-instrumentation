plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing")

  compileOnly("com.datastax.oss:java-driver-core:4.0.0")
  implementation("org.testcontainers:testcontainers")
}
