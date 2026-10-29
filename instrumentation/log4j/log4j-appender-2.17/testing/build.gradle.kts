plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing")
  api("io.opentelemetry:opentelemetry-sdk-testing")

  api("org.apache.logging.log4j:log4j-api:2.17.0")
}
