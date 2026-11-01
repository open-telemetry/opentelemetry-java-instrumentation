import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

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

    // SDK 3.4.3 covers the ProtostellarRequest constructor taking Core as its first argument.
    register<JvmTestSuite>("version343Test") {
      dependencies {
        implementation("com.couchbase.client:java-client:3.4.3")
      }
    }

    // SDK 3.4.4 covers the eight-argument ProtostellarRequest constructor taking CoreProtostellar.
    register<JvmTestSuite>("version344Test") {
      dependencies {
        implementation("com.couchbase.client:java-client:3.4.4")
      }
    }

    // RequestSpan.recordException is available in SDK 3.3.4+.
    register<JvmTestSuite>("version334Test") {
      dependencies {
        implementation("com.couchbase.client:java-client:${baseVersion("3.3.4").orLatest()}")
        implementation("org.testcontainers:testcontainers-couchbase")
      }
    }

    named<JvmTestSuite>("test") {
      executions {
        register("Experimental") {
          telemetryConfig("otel.instrumentation.couchbase.emit-experimental-telemetry", "true")
        }

        register("ExceptionSignalLogs") {
          filter {
            includeTestsMatching("*CouchbaseExceptionSignalTest")
          }
          telemetryConfig("otel.semconv.exception.signal.preview", "logs")
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    if (otelProps.denyUnsafe) {
      enabled = false
    }
  }

  check {
    dependsOn(testing.suites)
  }
}
