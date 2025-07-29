plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.projectreactor")
    module.set("reactor-core")
    versions.set("[3.4.0,)")
    extraDependency("io.opentelemetry:opentelemetry-api:1.0.0")
    assertInverse.set(true)
    excludeInstrumentationName("opentelemetry-api")
  }
}

dependencies {
  library("io.projectreactor:reactor-core:3.4.0")
  implementation(project(":instrumentation:reactor:reactor-3.1:library"))

  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))

  compileOnly(project(":javaagent-tooling"))
  compileOnly(project(":instrumentation-annotations-support"))
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))

  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:opentelemetry-extension-annotations-1.0:javaagent"))

  testLibrary("io.projectreactor:reactor-test:3.1.0.RELEASE")
  testImplementation(project(":instrumentation-annotations-support-testing"))
  testImplementation(project(":instrumentation:reactor:reactor-3.1:testing"))
  testImplementation(project(":instrumentation-annotations"))
  testImplementation("io.opentelemetry:opentelemetry-extension-annotations")
}
