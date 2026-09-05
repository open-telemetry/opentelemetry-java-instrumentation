plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[3.1,3.2)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:couchbase:couchbase-common-3.0:javaagent"))
  compileOnly(project(":muzzle")) // For @NoMuzzle

  // 3.1.4 (instead of 3.1.0) needed for test stability and for compatibility with server versions that run on M1 processors
  library("com.couchbase.client:java-client:3.1.4")

  testInstrumentation(project(":instrumentation:couchbase:couchbase-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-2.6:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.2:javaagent"))
  testImplementation("org.testcontainers:testcontainers-couchbase")

  latestDepTestLibrary("com.couchbase.client:java-client:3.1.+") // see couchbase-3.2 module
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  val testStableSemconvExperimental = register<Test>("testStableSemconvExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs(
      "-Dotel.semconv-stability.opt-in=database",
      "-Dotel.instrumentation.couchbase.experimental-span-attributes=true",
    )
    systemProperty(
      "metadataConfig",
      "otel.semconv-stability.opt-in=database,otel.instrumentation.couchbase.experimental-span-attributes=true",
    )
  }

  check {
    dependsOn(testStableSemconv, testStableSemconvExperimental)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
