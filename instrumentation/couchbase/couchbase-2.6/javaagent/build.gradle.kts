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
  implementation(project(":instrumentation:couchbase:couchbase-2-common:javaagent"))

  library("com.couchbase.client:java-client:2.6.0")

  testImplementation(project(":instrumentation:couchbase:couchbase-common:testing"))

  testLibrary("org.springframework.data:spring-data-couchbase:3.1.0.RELEASE")
  testLibrary("com.couchbase.client:encryption:1.0.0")

  testInstrumentation(project(":instrumentation:couchbase:couchbase-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.1.6:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.2:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.4:javaagent"))

  latestDepTestLibrary("org.springframework.data:spring-data-couchbase:3.1.+") // see couchbase-3.1 module
  latestDepTestLibrary("com.couchbase.client:java-client:2.+") // see couchbase-3.1 module
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  val testExperimental by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.couchbase.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.couchbase.experimental-span-attributes=true")
  }

  check {
    dependsOn(testStableSemconv, testExperimental)
  }

  if (findProperty("denyUnsafe") as Boolean) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
