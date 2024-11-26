plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.linecorp.armeria")
    module.set("armeria")
    versions.set("[1.3.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:armeria:armeria-1.3:library"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  library("com.linecorp.armeria:armeria:1.3.0")
  testLibrary("com.linecorp.armeria:armeria-junit5:1.3.0")

  testImplementation(project(":instrumentation:armeria:armeria-1.3:testing"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }
}
