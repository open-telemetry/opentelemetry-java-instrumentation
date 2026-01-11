plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")

  // earlier versions have bugs that may make tests flaky.
  implementation("org.apache.rocketmq:rocketmq-client-java:5.0.2")
  implementation("org.testcontainers:testcontainers")
  implementation("io.opentelemetry:opentelemetry-api")
}
