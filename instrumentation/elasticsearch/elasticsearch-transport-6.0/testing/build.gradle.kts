plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing")

  implementation("org.elasticsearch.client:transport:6.0.0")
  implementation(project(":instrumentation:elasticsearch:elasticsearch-transport-common-5.0:testing"))
  implementation("org.apache.logging.log4j:log4j-core:2.11.0")
  implementation("org.apache.logging.log4j:log4j-api:2.11.0")
}
