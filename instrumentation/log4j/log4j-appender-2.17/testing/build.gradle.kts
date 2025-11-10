plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")
  api("io.opentelemetry:opentelemetry-sdk-testing")

  api("org.apache.logging.log4j:log4j-api:2.17.0")
}
