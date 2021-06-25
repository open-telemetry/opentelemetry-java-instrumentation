plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.cxf")
    module.set("cxf-rt-rs-client")
    versions.set("[3.0.0,)")
  }
}

dependencies {
  library("org.apache.cxf:cxf-rt-rs-client:3.0.0")

  implementation(project(":instrumentation:jaxrs-client:jaxrs-client-2.0:jaxrs-client-2.0-common:javaagent"))
}
