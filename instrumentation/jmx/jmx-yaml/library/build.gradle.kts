plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("org.yaml:snakeyaml")
  implementation(project(":instrumentation:jmx:jmx-engine:library"))

  testImplementation(project(":testing-common"))
}
