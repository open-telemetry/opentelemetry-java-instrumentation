plugins {
  id("otel.javaagent-instrumentation")
}
muzzle {
  pass {
    group.set("dev.miku")
    module.set("r2dbc-mysql")
    versions.set("[0.8.2.RELEASE,)")
    assertInverse.set(true)
  }
}
dependencies {
  implementation(project(":instrumentation-api-annotation-support"))
  implementation(project(":instrumentation:jdbc:library"))
  library("dev.miku:r2dbc-mysql:0.8.2.RELEASE")
}
