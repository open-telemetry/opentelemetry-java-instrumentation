plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(path = ":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
}
