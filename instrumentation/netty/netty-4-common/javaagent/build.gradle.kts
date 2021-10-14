plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  compileOnly("io.netty:netty-codec-http:4.0.0.Final")
}
