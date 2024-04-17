plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
}

configurations.configureEach {
  if (name == "testRuntimeClasspath" || name == "testCompileClasspath") {
    resolutionStrategy {
      force("io.opentelemetry:opentelemetry-api:1.4.0")
    }
  }
}
