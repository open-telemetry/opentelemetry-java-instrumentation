plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
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
  implementation(project(":instrumentation:couchbase:couchbase-common-3.1:javaagent"))
  compileOnly(project(":muzzle")) // For @NoMuzzle

  // 3.1.4 (instead of 3.1.0) needed for test stability and for compatibility with server versions that run on M1 processors
  library("com.couchbase.client:java-client:3.1.4")

  testInstrumentation(project(":instrumentation:couchbase:couchbase-2.0:javaagent"))
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

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs(

      "-Dotel.instrumentation.couchbase.emit-experimental-telemetry=true",
    )
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.couchbase.emit-experimental-telemetry=true",
    )
  }

  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  val testV3PreviewExperimental = register<Test>("testV3PreviewExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs(
      "-Dotel.instrumentation.common.v3-preview=true",
      "-Dotel.instrumentation.couchbase.emit-experimental-telemetry=true",
    )
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.common.v3-preview=true,otel.instrumentation.couchbase.emit-experimental-telemetry=true",
    )
  }

  check {
    dependsOn(
      testExperimental,
      testV3Preview,
      testV3PreviewExperimental,
    )
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
