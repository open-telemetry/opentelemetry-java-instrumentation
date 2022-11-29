plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("org.redisson:redisson:3.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}
