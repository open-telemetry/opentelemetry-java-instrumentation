plugins {
  id("otel.javaagent-instrumentation")
  id("org.unbroken-dome.test-sets")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-rx-java2")
    versions.set("[3.5.0,)")
  }
}

testSets {
  create("version35Test")
  create("latestDepTest")
}

tasks {
  named<Test>("test") {
    dependsOn("version35Test")
  }
}

dependencies {
  // The first Vert.x version that uses rx-java 2
  compileOnly("io.vertx:vertx-web:3.5.0")
  compileOnly("io.vertx:vertx-rx-java2:3.5.0")

  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:rxjava:rxjava-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:vertx-http-client:vertx-http-client-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:vertx-http-client:vertx-http-client-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:vertx-web-3.0:javaagent"))

  testImplementation("org.hsqldb:hsqldb:2.3.4")

  add("version35TestImplementation", "io.vertx:vertx-web:3.5.0")
  add("version35TestImplementation", "io.vertx:vertx-rx-java2:3.5.0")
  add("version35TestImplementation", "io.vertx:vertx-web-client:3.5.0")
  add("version35TestImplementation", "io.vertx:vertx-jdbc-client:3.5.0")
  add("version35TestImplementation", "io.vertx:vertx-circuit-breaker:3.5.0")

  add("latestDepTestImplementation", "io.vertx:vertx-web:4.+")
  add("latestDepTestImplementation", "io.vertx:vertx-rx-java2:4.+")
  add("latestDepTestImplementation", "io.vertx:vertx-web-client:4.+")
  add("latestDepTestImplementation", "io.vertx:vertx-jdbc-client:4.+")
  add("latestDepTestImplementation", "io.vertx:vertx-circuit-breaker:4.+")
}
