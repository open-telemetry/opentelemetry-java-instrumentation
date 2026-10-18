import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}
muzzle {
  pass {
    group.set("commons-httpclient")
    module.set("commons-httpclient")
    versions.set("[2.0,4.0)")
    assertInverse.set(true)
  }
}

dependencies {
  library("commons-httpclient:commons-httpclient:2.0")

  latestDepTestLibrary("commons-httpclient:commons-httpclient:3.+") // see apache-httpclient-4.0 module

  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-5.0:javaagent"))
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          systemProperty("testLatestDeps", otelProps.testLatestDeps)
        }
        register("PreviewSemconv") {
          telemetryConfig("otel.semconv-stability.preview", "service.peer")
        }
      }
    }
  }
}
