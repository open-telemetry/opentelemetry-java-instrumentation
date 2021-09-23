plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.jboss.resteasy")
    module.set("resteasy-client")
    versions.set("[3.0.0.Final,)")
  }
}

dependencies {
  library("org.jboss.resteasy:resteasy-client:3.0.0.Final")

  implementation(project(":instrumentation:jaxrs-client:jaxrs-client-2.0:jaxrs-client-2.0-common:javaagent"))
}
