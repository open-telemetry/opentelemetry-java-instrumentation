plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[3.2.0,)")
    assertInverse.set(true)

    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.couchbase.v3_2.CouchbaseProtostellarInstrumentationModule")
  }
  pass {
    // instrumentation-docs:ignore - Couchbase Protostellar instrumentation only, the directive
    // above is the range we document
    name.set("Couchbase Protostellar instrumentation")
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[3.4.3,)")
    assertInverse.set(true)

    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.couchbase.v3_2.CouchbaseInstrumentationModule")
  }
}

dependencies {
  implementation(project(":instrumentation:couchbase:couchbase-common-3.0:javaagent"))
  implementation(project(":instrumentation:couchbase:couchbase-common-3.1:javaagent"))

  library("com.couchbase.client:java-client:3.2.0")
  compileOnly("com.couchbase.client:core-io:2.4.3") // For Protostellar types added in 3.4.3
  testCompileOnly("com.couchbase.client:java-client:3.12.0")

  testImplementation("org.testcontainers:testcontainers-couchbase")

  testInstrumentation(project(":instrumentation:couchbase:couchbase-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.1:javaagent"))

  latestDepTestLibrary("com.couchbase.client:java-client:+")
}

testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation(project(":instrumentation:couchbase:couchbase-common-3.1:javaagent"))
        implementation("com.couchbase.client:java-client:3.4.3")
        implementation("org.objenesis:objenesis")
      }
    }

    register<JvmTestSuite>("version343Test") {
      sources {
        java {
          setSrcDirs(listOf("src/version343Test/java"))
        }
      }
      dependencies {
        implementation("com.couchbase.client:java-client:3.4.3")
      }
    }

    register<JvmTestSuite>("version344Test") {
      sources {
        java {
          setSrcDirs(listOf("src/version344Test/java"))
        }
      }
      dependencies {
        implementation("com.couchbase.client:java-client:3.4.4")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty(
      "collectMetadata",
      otelProps.collectMetadata && name !in setOf("unitTests", "version343Test", "version344Test"),
    )
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

  check {
    dependsOn(testing.suites, testExperimental)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
