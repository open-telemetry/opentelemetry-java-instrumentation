plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("com.couchbase.mock:CouchbaseMock:1.5.19")
  // Earliest version that seems to allow queries with CouchbaseMock:
  api("com.couchbase.client:java-client:2.5.0")
  api("org.springframework.data:spring-data-couchbase:2.0.0.RELEASE")

  implementation("org.apache.groovy:groovy")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
