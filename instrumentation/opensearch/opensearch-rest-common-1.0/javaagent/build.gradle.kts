plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("org.opensearch.client:opensearch-rest-client:1.3.6")
  compileOnly("com.google.auto.value:auto-value-annotations")

  annotationProcessor("com.google.auto.value:auto-value")
}
