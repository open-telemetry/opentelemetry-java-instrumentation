plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "v1_47"))
  compileOnly("io.opentelemetry:opentelemetry-api-incubator")

  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.4:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.10:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.15:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.27:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.31:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.32:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.37:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.38:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.40:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.42:javaagent"))

  testImplementation("io.opentelemetry:opentelemetry-api-incubator")
}
