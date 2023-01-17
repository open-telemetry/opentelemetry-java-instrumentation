plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":instrumentation:jaxrs:jaxrs-common:testing"))
  api("javax.ws.rs:javax.ws.rs-api:2.0")

  compileOnly("org.eclipse.jetty:jetty-webapp:8.0.0.v20110901")
}