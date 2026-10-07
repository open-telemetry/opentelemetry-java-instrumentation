plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:pulsar:pulsar-2.8:javaagent"))
  testImplementation(project(":instrumentation:spring:spring-pulsar-1.0:javaagent"))
  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":javaagent-bootstrap"))
  testImplementation(project(":javaagent-extension-api"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.apache.pulsar:pulsar-client:2.8.0")
}
