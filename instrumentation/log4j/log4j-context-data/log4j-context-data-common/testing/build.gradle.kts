plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-instrumentation-testing")

  api("org.apache.logging.log4j:log4j-core:2.7")

  implementation("io.opentelemetry:opentelemetry-api")

  annotationProcessor("org.apache.logging.log4j:log4j-core:2.7")
}
