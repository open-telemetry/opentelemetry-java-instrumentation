plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.apache.logging.log4j:log4j-core:2.17.0")

  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}
