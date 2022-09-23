plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("org.yaml:snakeyaml")

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics")
  testImplementation(project(":testing-common"))
}
