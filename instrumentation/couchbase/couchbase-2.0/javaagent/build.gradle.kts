plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[2,3)")
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
  implementation(project(":instrumentation:couchbase:couchbase-2-common:javaagent"))
  implementation(project(":instrumentation:rxjava:rxjava-1.0:library"))

  library("com.couchbase.client:java-client:2.0.0")

  testImplementation(project(":instrumentation:couchbase:couchbase-common:testing"))

  latestDepTestLibrary("org.springframework.data:spring-data-couchbase:2.+") // see couchbase-2.6 module
  latestDepTestLibrary("com.couchbase.client:java-client:2.5.+") // see couchbase-2.6 module
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
