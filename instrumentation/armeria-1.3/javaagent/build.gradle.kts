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
  implementation(project(":instrumentation:armeria-1.3:library"))

  library("com.linecorp.armeria:armeria:1.3.0")

  testImplementation(project(":instrumentation:armeria-1.3:testing"))
}

tasks {
  test {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }
}