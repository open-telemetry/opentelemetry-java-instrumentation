plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  implementation("org.elasticsearch.client:transport:6.0.0")
  implementation(project(":instrumentation:elasticsearch:elasticsearch-transport-common:testing"))
  implementation("org.apache.logging.log4j:log4j-core:2.11.0")
  implementation("org.apache.logging.log4j:log4j-api:2.11.0")
}
