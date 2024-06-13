plugins {
  id("otel.javaagent-instrumentation")
}
muzzle {
  pass {
    group.set("io.javalin")
    module.set("javalin")
    versions.set("[5.0.0,)")
    assertInverse.set(true)
  }
}
dependencies {
  library("io.javalin:javalin:5.0.0")

  testInstrumentation(project(":instrumentation:jetty:jetty-11.0:javaagent"))

  testLibrary("io.javalin:javalin:5.0.0")
}
