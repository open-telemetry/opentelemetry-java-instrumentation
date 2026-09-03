plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("com.couchbase.client:java-client:3.1.4")
}
