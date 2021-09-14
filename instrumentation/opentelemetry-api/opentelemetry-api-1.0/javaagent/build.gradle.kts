plugins {
  id("otel.javaagent-instrumentation")
}

// TODO: add muzzle once 1.4.0 is released

dependencies {
  // this instrumentation needs to be able to reference both the OpenTelemetry API
  // that is shaded in the bootstrap class loader (for sending telemetry to the agent),
  // and the OpenTelemetry API that the user brings (in order to capture that telemetry)
  //
  // since (all) instrumentation already uses OpenTelemetry API for sending telemetry to the agent,
  // this instrumentation uses a "temporarily shaded" OpenTelemetry API to represent the
  // OpenTelemetry API that the user brings
  //
  // then later, after the OpenTelemetry API in the bootstrap class loader is shaded,
  // the "temporarily shaded" OpenTelemetry API is unshaded, so that it will apply to the
  // OpenTelemetry API that the user brings
  //
  // so in the code "application.io.opentelemetry.*" refers to the (unshaded) OpenTelemetry API that
  // the application brings (as those references will be translated during the build to remove the
  // "application." prefix)
  //
  // and in the code "io.opentelemetry.*" refers to the (shaded) OpenTelemetry API that is used by
  // the agent (as those references will later be shaded)
  compileOnly(project(path = ":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))

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
