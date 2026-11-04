import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("org.quartz-scheduler:quartz:2.0.0")

  testImplementation(project(":instrumentation:quartz-2.0:testing"))
}

testing.suites {
  executions("test") {
    register("Experimental") {
      systemProperty("test.captureExperimentalSpanAttributes", "true")
      systemProperty(
        "metadataConfig",
        "QuartzTelemetryBuilder.setCaptureExperimentalSpanAttributes(true)"
      )
    }
  }
}
