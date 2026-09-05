plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:couchbase:couchbase-common-3.1:javaagent"))
  testImplementation("com.couchbase.client:java-client:3.1.4")
}
