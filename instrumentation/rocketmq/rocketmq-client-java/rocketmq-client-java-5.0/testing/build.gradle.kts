plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  implementation("org.apache.rocketmq:rocketmq-client-java:5.0.2")
  implementation("org.testcontainers:testcontainers:1.17.5")
  implementation("io.opentelemetry:opentelemetry-api")
}
