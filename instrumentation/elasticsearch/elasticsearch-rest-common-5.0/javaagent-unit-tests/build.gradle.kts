plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:elasticsearch:elasticsearch-rest-common-5.0:bootstrap"))
  testImplementation(project(":instrumentation:elasticsearch:elasticsearch-rest-common-5.0:javaagent"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation("com.fasterxml.jackson.core:jackson-core")
  testImplementation("org.elasticsearch.client:rest:5.0.0")
}
