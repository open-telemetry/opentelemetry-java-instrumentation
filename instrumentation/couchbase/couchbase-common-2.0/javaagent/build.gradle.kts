plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  implementation(project(":instrumentation:couchbase:couchbase-common:javaagent"))

  compileOnly("com.couchbase.client:java-client:2.0.0")
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}
