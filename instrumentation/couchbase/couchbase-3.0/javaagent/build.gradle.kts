plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[3.0,3.1)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:couchbase:couchbase-common-3.0:javaagent"))

  library("com.couchbase.client:java-client:3.0.0")

  testImplementation("org.testcontainers:testcontainers-couchbase")

  testInstrumentation(project(":instrumentation:couchbase:couchbase-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-2.6:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.2:javaagent"))

  latestDepTestLibrary("com.couchbase.client:java-client:3.0.10") // no longer applicable
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
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
    dependsOn(testStableSemconv, testV3Preview, testV3PreviewExperimental)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
