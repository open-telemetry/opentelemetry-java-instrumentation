plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-kafka-client")
    versions.set("[3.5.1,)")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))

  compileOnly("io.vertx:vertx-kafka-client:3.6.0")
  // vertx-codegen is needed for Xlint's annotation checking
  compileOnly("io.vertx:vertx-codegen:3.6.0")
}
