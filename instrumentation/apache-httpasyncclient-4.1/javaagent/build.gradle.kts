import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.httpcomponents")
    module.set("httpasyncclient")
    // 4.0 and 4.0.1 don't copy over the traceparent (etc) http headers on redirect
    versions.set("[4.1,)")
    // TODO implement a muzzle check so that 4.0.x (at least 4.0 and 4.0.1) do not get applied
    //  and then bring back assertInverse
  }
}

dependencies {
  library("org.apache.httpcomponents:httpasyncclient:4.1")
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
