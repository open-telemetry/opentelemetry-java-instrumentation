plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":instrumentation:jaxrs:jaxrs-common:testing"))
  api("jakarta.ws.rs:jakarta.ws.rs-api:3.0.0")

  compileOnly("org.eclipse.jetty:jetty-webapp:11.0.0")
}
