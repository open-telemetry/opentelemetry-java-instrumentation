plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  compileOnly("io.nats:jnats:2.21.5")

  implementation("org.testcontainers:testcontainers")
}
