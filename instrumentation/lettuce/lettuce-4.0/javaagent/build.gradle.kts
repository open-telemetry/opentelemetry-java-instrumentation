import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("biz.paluch.redis")
    module.set("lettuce")
    versions.set("[4.0.Final,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("biz.paluch.redis:lettuce:4.0.Final")

  testInstrumentation(project(":instrumentation:lettuce:lettuce-5.0:javaagent"))

  latestDepTestLibrary("biz.paluch.redis:lettuce:4.+") // see lettuce-5.0 module
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("Experimental") {
          jvmArgs("-Dotel.instrumentation.lettuce.experimental-span-attributes=true")
          systemProperty("metadataConfig", "otel.instrumentation.lettuce.experimental-span-attributes=true")
        }
        register("ConnectionTelemetryEnabled") {
          jvmArgs("-Dotel.instrumentation.lettuce.connection-telemetry.enabled=true")
          systemProperty("metadataConfig", "otel.instrumentation.lettuce.connection-telemetry.enabled=true")
        }
      }
    }
  }
}
