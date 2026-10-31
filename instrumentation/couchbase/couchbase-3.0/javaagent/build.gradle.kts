import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
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
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.2:javaagent"))

  latestDepTestLibrary("com.couchbase.client:java-client:3.0.10") // no longer applicable
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }

  testing {
    suites {
      named<JvmTestSuite>("test") {
        executions {
          register("Experimental") {
            jvmArgs("-Dotel.instrumentation.couchbase.emit-experimental-telemetry=true")
            systemProperty("metadataConfig", "otel.instrumentation.couchbase.emit-experimental-telemetry=true")
          }
        }
      }
    }
  }
}
