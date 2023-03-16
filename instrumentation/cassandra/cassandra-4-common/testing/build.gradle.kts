plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  implementation("org.testcontainers:testcontainers:1.17.5")
  implementation("com.datastax.oss:java-driver-core:4.0.0")
}
