plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:couchbase:couchbase-3.1:javaagent"))
  testImplementation(project(":instrumentation:couchbase:couchbase-common:javaagent"))
  testImplementation(project(":instrumentation:couchbase:couchbase-common-3.1:javaagent"))
  testImplementation("com.couchbase.client:java-client:3.1.0")
  testImplementation("io.opentelemetry:opentelemetry-api")
}
