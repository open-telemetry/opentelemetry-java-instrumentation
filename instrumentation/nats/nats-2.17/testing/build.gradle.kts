plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-instrumentation-testing")

  compileOnly("io.nats:jnats:2.17.2")

  implementation("org.testcontainers:testcontainers")
}
