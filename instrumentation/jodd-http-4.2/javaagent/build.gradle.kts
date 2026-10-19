import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.jodd")
    module.set("jodd-http")
    versions.set("[4.1.1,)")
    assertInverse.set(true)
  }
}

dependencies {
  // 4.1.1 is the first version with HttpBase#headerOverwrite used by header injection
  library("org.jodd:jodd-http:4.1.1")

  testImplementation(project(":instrumentation-api-incubator"))
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
