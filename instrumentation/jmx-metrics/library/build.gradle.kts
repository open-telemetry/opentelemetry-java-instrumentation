plugins {
  id("otel.library-instrumentation")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.jmx")

dependencies {
  implementation("org.snakeyaml:snakeyaml-engine")

  testImplementation(project(":testing-common"))
}
