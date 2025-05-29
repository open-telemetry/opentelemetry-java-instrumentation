plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  implementation("org.apache.rocketmq:rocketmq-test:4.8.0")

  implementation("io.opentelemetry:opentelemetry-api")
}
