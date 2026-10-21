import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.thrift")
    module.set("libthrift")
    versions.set("[0.13.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.thrift:libthrift:0.13.0")
  implementation(project(":instrumentation:thrift-0.13:library"))
  testImplementation(project(":instrumentation:thrift-0.13:testing"))
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          systemProperty("testLatestDeps", otelProps.testLatestDeps)
        }
        register("PreviewSemconv") {
          telemetryConfig("otel.semconv-stability.preview", "rpc")
        }
        register("BothSemconv") {
          telemetryConfig("otel.semconv-stability.preview", "rpc/dup")
        }
      }
    }
  }
}
