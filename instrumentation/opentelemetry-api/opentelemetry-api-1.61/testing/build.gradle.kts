plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.27:javaagent"))
}

configurations.configureEach {
  if (name.endsWith("testRuntimeClasspath", true) || name.endsWith("testCompileClasspath", true)) {
    resolutionStrategy {
      force("io.opentelemetry:opentelemetry-api:1.61.0")
      force("io.opentelemetry:opentelemetry-api-incubator:1.61.0-alpha")
    }
  }
}
