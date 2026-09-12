plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:opensearch:opensearch-rest-common-1.0:javaagent"))
  testImplementation("org.apache.httpcomponents:httpcore:4.4.16")
}
