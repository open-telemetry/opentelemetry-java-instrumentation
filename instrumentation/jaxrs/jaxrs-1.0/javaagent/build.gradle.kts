plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("javax.ws.rs")
    module.set("jsr311-api")
    versions.set("[0.5,)")
  }
  fail {
    group.set("javax.ws.rs")
    module.set("javax.ws.rs-api")
    versions.set("[,]")
  }
}

dependencies {
  compileOnly(project(":instrumentation:jaxrs:jaxrs-common:bootstrap"))

  compileOnly("javax.ws.rs:jsr311-api:1.1.1")

  testImplementation("io.dropwizard:dropwizard-testing:0.7.1")
  testImplementation("javax.xml.bind:jaxb-api:2.2.3")
}
