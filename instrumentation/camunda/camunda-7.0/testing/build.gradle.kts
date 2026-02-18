plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  implementation("org.camunda.bpm:camunda-engine:7.18.0")

  implementation("io.opentelemetry:opentelemetry-api")
  implementation("com.h2database:h2:2.2.224")
}
