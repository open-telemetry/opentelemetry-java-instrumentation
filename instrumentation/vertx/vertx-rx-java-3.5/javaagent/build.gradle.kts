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
  test {
    dependsOn("version35Test")
  }
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.db-statement-sanitizer.enabled=true")
}

// The first Vert.x version that uses rx-java 2
val vertxVersion = "3.5.0"

dependencies {
  compileOnly("io.vertx:vertx-web:$vertxVersion")
  compileOnly("io.vertx:vertx-rx-java2:$vertxVersion")

  testCompileOnly("io.vertx:vertx-codegen:$vertxVersion")

  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:rxjava:rxjava-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-http-client:vertx-http-client-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-http-client:vertx-http-client-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-web-3.0:javaagent"))

  testImplementation("org.hsqldb:hsqldb:2.3.4")

  add("version35TestImplementation", "io.vertx:vertx-web:$vertxVersion")
  add("version35TestImplementation", "io.vertx:vertx-rx-java2:$vertxVersion")
  add("version35TestImplementation", "io.vertx:vertx-web-client:$vertxVersion")
  add("version35TestImplementation", "io.vertx:vertx-jdbc-client:$vertxVersion")
  add("version35TestImplementation", "io.vertx:vertx-circuit-breaker:$vertxVersion")

  // concurrency tests are failing with 4.3.4
  // tracking at https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/6790
  add("latestDepTestImplementation", "io.vertx:vertx-web:4.3.3")
  add("latestDepTestImplementation", "io.vertx:vertx-rx-java2:4.3.3")
  add("latestDepTestImplementation", "io.vertx:vertx-web-client:4.3.3")
  add("latestDepTestImplementation", "io.vertx:vertx-jdbc-client:4.3.3")
  add("latestDepTestImplementation", "io.vertx:vertx-circuit-breaker:4.3.3")
}
