plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  api("io.opentelemetry:opentelemetry-sdk-testing")

  compileOnly("org.apache.logging.log4j:log4j-api:2.17.0")
}
