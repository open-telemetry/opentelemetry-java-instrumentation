plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.mchange")
    module.set("c3p0")
    versions.set("[0.9.2,)")
    assertInverse.set(true)
    // these versions have missing dependencies in maven central
    skip("0.9.2-pre2-RELEASE", "0.9.2-pre3")
  }
}

dependencies {
  library("com.mchange:c3p0:0.9.2")

  implementation(project(":instrumentation:c3p0-0.9:library"))

  testImplementation(project(":instrumentation:c3p0-0.9:testing"))
}
