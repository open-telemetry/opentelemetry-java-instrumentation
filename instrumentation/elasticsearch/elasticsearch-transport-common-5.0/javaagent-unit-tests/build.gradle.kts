plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(
    project(
      ":instrumentation:elasticsearch:elasticsearch-transport-common-5.0:javaagent",
    ),
  )

  testImplementation("org.elasticsearch.client:transport:5.0.0")
  testImplementation("org.apache.logging.log4j:log4j-api:2.11.0")
  testImplementation("org.apache.logging.log4j:log4j-core:2.11.0")
}
