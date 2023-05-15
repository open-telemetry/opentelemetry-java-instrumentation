plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.quarkus")
    module.set("quarkus-resteasy-reactive")
    versions.set("(,)")
  }
}

dependencies {
  compileOnly("io.quarkus:quarkus-resteasy-reactive:1.11.0.Final")
  implementation(project(":instrumentation:jaxrs:jaxrs-common:javaagent"))
}
