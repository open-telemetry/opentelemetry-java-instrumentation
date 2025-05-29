plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":javaagent-extension-api"))
  testImplementation(project(":instrumentation:couchbase:couchbase-2-common:javaagent"))
  testImplementation("com.couchbase.client:java-client:2.5.0")
}
