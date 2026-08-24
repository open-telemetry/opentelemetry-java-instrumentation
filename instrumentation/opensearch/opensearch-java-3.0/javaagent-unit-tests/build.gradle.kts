plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:opensearch:opensearch-java-3.0:javaagent"))
}
