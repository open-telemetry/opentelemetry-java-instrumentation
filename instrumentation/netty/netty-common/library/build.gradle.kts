plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")
}
