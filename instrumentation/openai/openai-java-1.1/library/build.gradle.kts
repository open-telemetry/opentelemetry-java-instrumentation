import io.opentelemetry.instrumentation.gradle.executions

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
    register("ExceptionSignalLogs") {
      jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
    }
  }
}
