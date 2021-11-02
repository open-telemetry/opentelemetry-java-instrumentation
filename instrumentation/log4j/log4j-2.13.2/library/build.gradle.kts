plugins {
  id("otel.library-instrumentation")
}

dependencies {
  api("io.opentelemetry:opentelemetry-sdk-logs:1.8.0-alpha-SNAPSHOT")

  library("org.apache.logging.log4j:log4j-core:2.13.2")

  // Library instrumentation cannot be applied to 2.13.2 due to a bug in Log4J. The agent works
  // around it.
  testLibrary("org.apache.logging.log4j:log4j-core:2.13.3")

  testImplementation(project(":instrumentation:log4j:log4j-2-common:testing"))
}
