plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "v1_10"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.4:javaagent"))
}

configurations.configureEach {
  if (name == "testRuntimeClasspath" || name == "testCompileClasspath") {
    resolutionStrategy {
      force("io.opentelemetry:opentelemetry-api:1.10.0")
    }
  }
}
