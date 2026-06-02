plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "v1_57"))
}

configurations.configureEach {
  if (name.endsWith("testRuntimeClasspath", true) || name.endsWith("testCompileClasspath", true)) {
    resolutionStrategy {
      force("io.opentelemetry:opentelemetry-api:1.57.0")
      force("io.opentelemetry:opentelemetry-api-incubator:1.57.0-alpha")
    }
  }
}
