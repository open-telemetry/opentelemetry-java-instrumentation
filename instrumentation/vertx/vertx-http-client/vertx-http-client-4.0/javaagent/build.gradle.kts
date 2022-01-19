plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-core")
    versions.set("[4.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.vertx:vertx-core:4.0.0")

  compileOnly("io.vertx:vertx-codegen:4.0.0")

  implementation(project(":instrumentation:vertx:vertx-http-client:vertx-http-client-common:javaagent"))

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
}
