plugins {
  id("otel.javaagent-instrumentation")
  id("org.unbroken-dome.test-sets")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-web")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
  }
}

testSets {
  create("version3Test")
  create("latestDepTest")
}

tasks {
  named<Test>("test") {
    dependsOn("version3Test")
  }
}

dependencies {
  compileOnly("io.vertx:vertx-web:3.0.0")

  // We need both version as different versions of Vert.x use different versions of Netty
  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:jdbc:javaagent"))

  testImplementation(project(":instrumentation:vertx-web-3.0:testing"))

  add("version3TestImplementation", "io.vertx:vertx-web:3.0.0")
  add("version3TestImplementation", "io.vertx:vertx-jdbc-client:3.0.0")

  add("latestDepTestImplementation", "io.vertx:vertx-web:4.+")
  add("latestDepTestImplementation", "io.vertx:vertx-jdbc-client:4.+")
}
