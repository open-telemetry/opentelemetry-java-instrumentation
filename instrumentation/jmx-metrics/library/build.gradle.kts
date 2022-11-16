plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("org.yaml:snakeyaml")

  testImplementation(project(":testing-common"))
}
