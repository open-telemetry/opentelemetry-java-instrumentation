plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "v1_59"))

  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
}

configurations.configureEach {
  if (name.endsWith("testRuntimeClasspath", true) || name.endsWith("testCompileClasspath", true)) {
    resolutionStrategy {
      force("io.opentelemetry:opentelemetry-api:1.59.0")
      force("io.opentelemetry:opentelemetry-api-incubator:1.59.0-alpha")
    }
  }
}
