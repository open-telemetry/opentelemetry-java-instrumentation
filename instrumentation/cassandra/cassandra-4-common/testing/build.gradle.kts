plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  compileOnly("com.datastax.oss:java-driver-core:4.0.0")
  implementation("org.testcontainers:testcontainers")
}
