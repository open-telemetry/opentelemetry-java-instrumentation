plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation-api"))

  testImplementation(project(":testing-common"))
}
