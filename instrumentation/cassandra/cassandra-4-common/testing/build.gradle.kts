plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  implementation("org.testcontainers:testcontainers")
  implementation("com.datastax.oss:java-driver-core:4.0.0")
}
