import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
  id("otel.animalsniffer-conventions")
  id("otel.osgi-conventions")
}

dependencies {
  library("org.apache.httpcomponents:httpclient:4.3")

  testImplementation(project(":instrumentation:apache-httpclient:apache-httpclient-4.3:testing"))
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
