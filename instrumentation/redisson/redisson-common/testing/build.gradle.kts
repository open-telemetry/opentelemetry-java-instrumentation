plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.testcontainers:testcontainers")

  compileOnly("org.redisson:redisson:3.7.2")
}
