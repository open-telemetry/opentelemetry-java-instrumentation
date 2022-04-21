plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  implementation("io.opentelemetry:opentelemetry-extension-annotations")
}
