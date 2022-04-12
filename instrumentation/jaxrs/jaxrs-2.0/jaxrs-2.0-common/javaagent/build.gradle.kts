plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  bootstrap(project(":instrumentation:jaxrs:jaxrs-common:bootstrap"))

  compileOnly("javax.ws.rs:javax.ws.rs-api:2.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}
