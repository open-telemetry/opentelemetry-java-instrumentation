plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  api(project(":instrumentation:netty:netty-common:javaagent"))

  compileOnly("io.netty:netty-codec-http:4.0.0.Final")
}
