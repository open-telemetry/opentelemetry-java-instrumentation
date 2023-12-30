plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  // 6.0+ added protocolVersion access which allows forcing RESP2 for consistency in tests
  compileOnly("io.lettuce:lettuce-core:6.0.0.RELEASE")

  implementation("org.testcontainers:testcontainers")
  implementation("com.google.guava:guava")

  implementation("io.opentelemetry:opentelemetry-api")
}
