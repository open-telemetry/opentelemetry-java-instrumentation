plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation-api"))
  implementation("io.opentelemetry:opentelemetry-extension-incubator")

  testImplementation(project(":testing-common"))
}
