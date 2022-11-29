plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.vibur")
    module.set("vibur-dbcp")
    versions.set("[11.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.vibur:vibur-dbcp:11.0")

  implementation(project(":instrumentation:vibur-dbcp-11.0:library"))

  testImplementation(project(":instrumentation:vibur-dbcp-11.0:testing"))
}
