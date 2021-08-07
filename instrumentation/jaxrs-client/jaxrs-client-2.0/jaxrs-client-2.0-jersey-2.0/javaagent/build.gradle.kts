plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.glassfish.jersey.core")
    module.set("jersey-client")
    versions.set("[2.0,3.0.0)")
  }
}

dependencies {
  library("org.glassfish.jersey.core:jersey-client:2.0")

  implementation(project(":instrumentation:jaxrs-client:jaxrs-client-2.0:jaxrs-client-2.0-common:javaagent"))
}
