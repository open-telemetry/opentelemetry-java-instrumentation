plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-instrumentation-testing")

  compileOnly("org.mongodb:mongodb-driver-core:3.3.0")

  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.testcontainers:testcontainers")
}
