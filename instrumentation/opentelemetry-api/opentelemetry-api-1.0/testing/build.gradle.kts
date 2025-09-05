plugins {
  id("otel.javaagent-testing")
}

dependencies {
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
}
