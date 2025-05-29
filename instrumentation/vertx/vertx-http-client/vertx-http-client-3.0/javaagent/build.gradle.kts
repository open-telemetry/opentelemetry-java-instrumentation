plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-core")
    versions.set("[3.0.0,4.0.0)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.vertx:vertx-core:3.0.0")

  // vertx-codegen and vertx-docgen dependencies are needed for Xlint's annotation checking
  library("io.vertx:vertx-codegen:3.0.0")
  testLibrary("io.vertx:vertx-docgen:3.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":instrumentation:vertx:vertx-http-client:vertx-http-client-common:javaagent"))

  // We need both version as different versions of Vert.x use different versions of Netty
  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  // 3.9.7 Requires Netty 4.1.60, no other version works with it.
  latestDepTestLibrary(enforcedPlatform("io.netty:netty-bom:4.1.60.Final")) // see vertx-http-client-4.0 module
  latestDepTestLibrary("io.vertx:vertx-core:3.+") // see vertx-http-client-4.0 module
  latestDepTestLibrary("io.vertx:vertx-codegen:3.+") // see vertx-http-client-4.0 module
  latestDepTestLibrary("io.vertx:vertx-docgen:3.+") // see vertx-http-client-4.0 module
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }
}
