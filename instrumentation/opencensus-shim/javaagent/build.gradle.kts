plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.opentelemetry")
    module.set("opentelemetry-opencensus-shim")
    versions.set("[1.18.0-alpha,)")
  }
}

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-opencensus-shim")
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))

  testImplementation(project(":instrumentation:opencensus-shim:testing"))
  testInstrumentation(project(":instrumentation:opencensus-shim:testing"))
  testImplementation(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))
}
