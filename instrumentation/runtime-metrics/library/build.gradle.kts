plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("org.yaml:snakeyaml:1.30")

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics")
  testImplementation(project(":testing-common"))
}
