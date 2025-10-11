plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-core")
    versions.set("[4.0.0,5)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.vertx:vertx-core:4.0.0")

  // vertx-codegen dependency is needed for Xlint's annotation checking
  library("io.vertx:vertx-codegen:4.0.0")

  implementation(project(":instrumentation:vertx:vertx-http-client:vertx-http-client-common:javaagent"))

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  latestDepTestLibrary("io.vertx:vertx-core:4.+") // see vertx-http-client-5.0 module
  latestDepTestLibrary("io.vertx:vertx-codegen:4.+") // see vertx-http-client-5.0 module
}

tasks {
  test {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
    systemProperty("otel.instrumentation.common.peer-service-mapping", "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service")
  }
}
