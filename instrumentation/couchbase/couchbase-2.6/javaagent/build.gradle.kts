plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[2.6.0,3)")
    // these versions were released as ".bundle" instead of ".jar"
    skip("2.7.5", "2.7.8")
    assertInverse.set(true)
  }
  fail {
    group.set("com.couchbase.client")
    module.set("couchbase-client")
    versions.set("(,)")
  }
}

dependencies {
  implementation(project(":instrumentation:rxjava:rxjava-1.0:library"))
  implementation(project(":instrumentation:couchbase:couchbase-2.0:javaagent"))

  library("com.couchbase.client:java-client:2.6.0")

  testInstrumentation(project(":instrumentation:couchbase:couchbase-2.0:javaagent"))
  testImplementation(project(":instrumentation:couchbase:couchbase-common:testing"))

  testLibrary("org.springframework.data:spring-data-couchbase:3.1.0.RELEASE")
  testLibrary("com.couchbase.client:encryption:1.0.0")

  latestDepTestLibrary("org.springframework.data:spring-data-couchbase:3.1.+")
  latestDepTestLibrary("com.couchbase.client:java-client:2.+")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.couchbase.experimental-span-attributes=true")
}
