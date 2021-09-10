plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(path = ":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))

  // using OpenTelemetry SDK to make sure that instrumentation doesn't cause
  // OpenTelemetrySdk.getTracerProvider() to throw ClassCastException
  testImplementation("io.opentelemetry:opentelemetry-sdk")
  testImplementation(project(":instrumentation-api"))

  // @WithSpan annotation is used to generate spans in ContextBridgeTest
  testImplementation("io.opentelemetry:opentelemetry-extension-annotations")
  testInstrumentation(project(":instrumentation:opentelemetry-annotations-1.0:javaagent"))

  testImplementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:testing"))
  testInstrumentation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:testing"))
}
