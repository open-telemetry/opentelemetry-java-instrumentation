plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.javalin")
    module.set("javalin")
    versions.set("[5.0.0,7.0.0)")
    // 3.2.0 depends on org.meteogroup.jbrotli:jbrotli:0.5.0 that is not available in central
    skip("3.2.0")
    assertInverse.set(true)
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  library("io.javalin:javalin:5.0.0")

  testImplementation(project(":instrumentation:javalin:testing"))

  testInstrumentation(project(":instrumentation:jetty:jetty-11.0:javaagent"))
  // Ensure no cross interference
  testInstrumentation(project(":instrumentation:javalin:javalin-7.0:javaagent"))

  latestDepTestLibrary("io.javalin:javalin:6.+") // see javalin-7.0 module
}
