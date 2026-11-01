import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.testcontainers-testing")
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

testing.suites.named<JvmTestSuite>("test") {
  executions {
    configureEach {
      systemProperty("testLatestDeps", otelProps.testLatestDeps)
    }
    register("Experimental") {
      telemetryConfig("otel.instrumentation.lettuce.experimental-span-attributes", "true")
    }
    register("ConnectionTelemetryEnabled") {
      telemetryConfig("otel.instrumentation.lettuce.connection-telemetry.enabled", "true")
    }
  }
}
