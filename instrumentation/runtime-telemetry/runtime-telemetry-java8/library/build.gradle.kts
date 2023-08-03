plugins {
  id("otel.library-instrumentation")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.runtimemetrics.java8")

dependencies {
  implementation(project(":instrumentation-api"))
  implementation("io.opentelemetry:opentelemetry-extension-incubator")

  testImplementation(project(":testing-common"))
}
