plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing")

  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.testcontainers:testcontainers")

  compileOnly("org.redisson:redisson:2.3.0")
}
