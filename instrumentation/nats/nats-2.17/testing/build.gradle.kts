plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  compileOnly("io.nats:jnats:2.17.2")

  implementation("org.testcontainers:testcontainers")
}
