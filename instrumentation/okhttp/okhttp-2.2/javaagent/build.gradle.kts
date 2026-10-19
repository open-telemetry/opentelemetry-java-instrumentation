import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

/*
Note: The Interceptor class for OkHttp was not introduced until 2.2+, so we need to make sure the
instrumentation is not loaded unless the dependency is 2.2+.
*/
muzzle {
  pass {
    group.set("com.squareup.okhttp")
    module.set("okhttp")
    versions.set("[2.2,3)")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  library("com.squareup.okhttp:okhttp:2.2.0")

  testInstrumentation(project(":instrumentation:okhttp:okhttp-3.0:javaagent"))
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("PreviewSemconv") {
          telemetryConfig("otel.semconv-stability.preview", "service.peer")
        }
      }
    }
  }
}
