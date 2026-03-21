plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.opentelemetry")
    module.set("opentelemetry-api")
    versions.set("[1.59.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "v1_59"))

  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
}
