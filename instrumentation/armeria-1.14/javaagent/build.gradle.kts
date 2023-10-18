plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.linecorp.armeria")
    module.set("armeria")
    versions.set("[1.14.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:armeria-1.14:library"))
  implementation(project(":instrumentation:grpc-1.6:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  library("com.linecorp.armeria:armeria:1.14.0")

  testImplementation(project(":instrumentation:armeria-1.14:testing"))
}

tasks {
  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=http")
  }

  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }

  check {
    dependsOn(testStableSemconv)
  }
}
