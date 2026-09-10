plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:couchbase:couchbase-common-3.0:javaagent"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":javaagent-extension-api"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}
