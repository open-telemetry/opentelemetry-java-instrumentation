plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-rx-java2")
    versions.set("[3.5.0,)")
  }
}

//The first Vert.x version that uses rx-java 2
val vertxVersion = "3.5.0"

dependencies {
  library("io.vertx:vertx-web:${vertxVersion}")
  library("io.vertx:vertx-rx-java2:${vertxVersion}")

  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:vertx-http-client:vertx-http-client-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:vertx-web-3.0:javaagent"))
  //TODO we should include rjxava2 instrumentation here as well

  testLibrary("io.vertx:vertx-web-client:${vertxVersion}")
  testLibrary("io.vertx:vertx-jdbc-client:${vertxVersion}")
  testLibrary("io.vertx:vertx-circuit-breaker:${vertxVersion}")
  testImplementation("org.hsqldb:hsqldb:2.3.4")

  // Vert.x 4.0 is incompatible with our tests.
  // 3.9.7 Requires Netty 4.1.60, no other version works with it.
  latestDepTestLibrary(enforcedPlatform("io.netty:netty-bom:4.1.60.Final"))
  latestDepTestLibrary("io.vertx:vertx-web:3.+")
  latestDepTestLibrary("io.vertx:vertx-web-client:3.+")
  latestDepTestLibrary("io.vertx:vertx-jdbc-client:3.+")
  latestDepTestLibrary("io.vertx:vertx-circuit-breaker:3.+")
  latestDepTestLibrary("io.vertx:vertx-rx-java2:3.+")
}

tasks {
  named<Test>("test") {
    systemProperty("testLatestDeps", findProperty("testLatestDeps"))
  }
}
