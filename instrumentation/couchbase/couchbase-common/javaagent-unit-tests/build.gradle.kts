plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:couchbase:couchbase-common:javaagent"))
}
