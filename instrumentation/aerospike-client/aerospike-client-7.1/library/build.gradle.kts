plugins {
  id("otel.library-instrumentation")
  id("otel.animalsniffer-conventions")
}

dependencies {
  implementation("io.opentelemetry:opentelemetry-extension-incubator")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}
