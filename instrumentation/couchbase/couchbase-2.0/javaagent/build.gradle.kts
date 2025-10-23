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

  testInstrumentation(project(":instrumentation:couchbase:couchbase-2.6:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.1.6:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.2:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.4:javaagent"))

  latestDepTestLibrary("org.springframework.data:spring-data-couchbase:2.+") // see couchbase-2.6 module
  latestDepTestLibrary("com.couchbase.client:java-client:2.5.+") // see couchbase-2.6 module
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }

  if (findProperty("denyUnsafe") as Boolean) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
