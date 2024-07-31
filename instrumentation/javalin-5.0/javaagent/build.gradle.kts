plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.javalin")
    module.set("javalin")
    versions.set("[5.0.0,)")
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

  testInstrumentation(project(":instrumentation:jetty:jetty-11.0:javaagent"))
}
