plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation("org.apache.groovy:groovy")
  testImplementation("org.spockframework:spock-core")

  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":instrumentation:couchbase:couchbase-2-common:javaagent"))
  testImplementation("com.couchbase.client:java-client:2.5.0")
}
