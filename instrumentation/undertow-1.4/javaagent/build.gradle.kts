plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.undertow")
    module.set("undertow-core")
    versions.set("[1.4.0.Final,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.undertow:undertow-core:2.0.0.Final")

  compileOnly(project(":instrumentation:servlet:servlet-common:bootstrap"))
  compileOnly(project(":instrumentation:undertow-1.4:bootstrap"))
}
