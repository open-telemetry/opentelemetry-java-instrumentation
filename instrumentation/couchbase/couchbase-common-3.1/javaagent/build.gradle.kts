plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:couchbase:couchbase-common:javaagent"))

  compileOnly("com.couchbase.client:java-client:3.1.4")
}
