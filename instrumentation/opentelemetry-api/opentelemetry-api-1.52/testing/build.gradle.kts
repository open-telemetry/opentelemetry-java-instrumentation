plugins {
  id("otel.javaagent-testing")
}

dependencies {
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
