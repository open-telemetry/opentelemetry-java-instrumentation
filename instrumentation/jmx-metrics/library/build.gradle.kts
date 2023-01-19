plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("org.snakeyaml:snakeyaml-engine")

  testImplementation(project(":testing-common"))
}
