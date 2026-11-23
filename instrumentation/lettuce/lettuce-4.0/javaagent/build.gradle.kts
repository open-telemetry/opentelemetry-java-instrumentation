import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig
import io.opentelemetry.instrumentation.gradle.unitTests

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.testcontainers-testing")
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

testing.suites {
  unitTests {
    dependencies {
      implementation(project(":javaagent-extension-api"))
      implementation("biz.paluch.redis:lettuce:4.0.Final")
      implementation("io.netty:netty-transport-native-epoll:4.0.56.Final")
    }
  }

  executions("test") {
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
