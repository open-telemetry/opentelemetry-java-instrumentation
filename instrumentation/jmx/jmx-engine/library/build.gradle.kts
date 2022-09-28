plugins {
  id("otel.library-instrumentation")
}

dependencies {

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  testImplementation(project(":testing-common"))
}
