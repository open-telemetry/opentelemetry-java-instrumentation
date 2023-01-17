plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  bootstrap(project(":instrumentation:jaxrs:jaxrs-common:bootstrap"))
  api(project(":instrumentation:jaxrs:jaxrs-common:javaagent"))

  compileOnly("jakarta.ws.rs:jakarta.ws.rs-api:3.0.0")
}
