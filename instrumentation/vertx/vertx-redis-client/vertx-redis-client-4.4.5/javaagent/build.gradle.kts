plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-redis-client")
    versions.set("[4.4.5,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly(project(":instrumentation:vertx:vertx-redis-client:vertx-redis-client-4.0:javaagent"))

  library("io.vertx:vertx-redis-client:4.4.5")
  compileOnly("io.vertx:vertx-codegen:4.4.5")

  testInstrumentation(project(":instrumentation:vertx:vertx-redis-client:vertx-redis-client-4.0:javaagent"))
}
