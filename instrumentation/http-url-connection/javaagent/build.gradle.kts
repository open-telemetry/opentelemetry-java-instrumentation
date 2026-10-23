import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk.set(true)
  }
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
      }
    }
  }
}
