plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.oracle.database.jdbc")
    module.set("ucp")
    versions.set("[,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.oracle.database.jdbc:ucp:11.2.0.4")

  implementation(project(":instrumentation:oracle-ucp-11.2:library"))

  testImplementation(project(":instrumentation:oracle-ucp-11.2:testing"))
}
