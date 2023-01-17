plugins {
  id("com.github.johnrengelman.shadow")

  id("otel.java-conventions")
}

description = "opentelemetry-instrumentation-api shaded for internal javaagent usage"
group = "io.opentelemetry.javaagent"

dependencies {
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api")
}

// OpenTelemetry Instrumentation API shaded so that it can be used in instrumentation of
// OpenTelemetry Instrumentation API itself,
// and then its usage can be unshaded after OpenTelemetry Instrumentation API is shaded
// (see more explanation in opentelemetry-api-1.0.gradle)
tasks {
  shadowJar {
    relocate("io.opentelemetry.instrumentation.api", "application.io.opentelemetry.instrumentation.api")
  }
}
