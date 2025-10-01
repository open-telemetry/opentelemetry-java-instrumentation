plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-core")
    versions.set("[3.9.0,4.0.0)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("io.vertx:vertx-core:3.9.0")
  compileOnly("io.vertx:vertx-codegen:3.9.0")
}
