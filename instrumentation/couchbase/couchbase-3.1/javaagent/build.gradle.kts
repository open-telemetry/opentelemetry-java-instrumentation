plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[3.1,3.1.6)")
    // these versions were released as ".bundle" instead of ".jar"
    skip("2.7.5", "2.7.8")
    assertInverse.set(true)
  }
}

val versions: Map<String, String> by project

dependencies {
  implementation("com.couchbase.client:tracing-opentelemetry:0.3.3") {
    exclude("com.couchbase.client", "core-io")
  }

  library("com.couchbase.client:core-io:2.1.0")

  testLibrary("com.couchbase.client:java-client:3.1.0")

  testImplementation("org.testcontainers:couchbase:${versions["org.testcontainers"]}")

  latestDepTestLibrary("com.couchbase.client:java-client:3.1.5")
  latestDepTestLibrary("com.couchbase.client:core-io:2.1.5")
}