plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.50:javaagent"))

  testImplementation("io.opentelemetry:opentelemetry-api-incubator")
}

configurations.configureEach {
  if (name.endsWith("testRuntimeClasspath", true) || name.endsWith("testCompileClasspath", true)) {
    resolutionStrategy {
      force("io.opentelemetry:opentelemetry-api:1.52.0")
      force("io.opentelemetry:opentelemetry-api-incubator:1.52.0-alpha")
    }
  }
}
