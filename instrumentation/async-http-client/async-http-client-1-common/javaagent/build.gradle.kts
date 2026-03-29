plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("com.ning:async-http-client:1.8.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}
