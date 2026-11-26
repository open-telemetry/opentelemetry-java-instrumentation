import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("com.openai:openai-java:1.1.0")

  testImplementation(project(":instrumentation:openai:openai-java-1.1:testing"))
}

testing.suites {
  executions("test") {
    configureEach {
      systemProperty("testLatestDeps", otelProps.testLatestDeps)
    }
    register("PreviewSemconv") {
      telemetryConfig("otel.semconv-stability.preview", "gen_ai")
    }
    register("ExceptionSignalLogs") {
      jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
    }
  }
}
