plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.activej:activej-http")
    module.set("activej-http")

    versions.set("[1.0,)")
  }
}

dependencies {
  library("io.activej:activej-http:6.0-beta2")
}
