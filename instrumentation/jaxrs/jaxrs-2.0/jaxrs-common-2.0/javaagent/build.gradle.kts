plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  bootstrap(project(":instrumentation:jaxrs:jaxrs-common:bootstrap"))
  api(project(":instrumentation:jaxrs:jaxrs-common:javaagent"))

  compileOnly("javax.ws.rs:javax.ws.rs-api:2.0")
}
