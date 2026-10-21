import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
  id("otel.animalsniffer-conventions")
}

dependencies {
  library("org.apache.httpcomponents.client5:httpclient5:5.2.1")
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
