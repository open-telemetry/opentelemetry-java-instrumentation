plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":instrumentation:jaxrs:bootstrap"))

  compileOnly("javax.ws.rs:javax.ws.rs-api:2.0")
  compileOnly("org.jboss.resteasy:resteasy-jaxrs:3.1.0.Final")

  implementation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-common:javaagent"))
}
