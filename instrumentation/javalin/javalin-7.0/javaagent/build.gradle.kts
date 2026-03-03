plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.javalin")
    module.set("javalin")
    versions.set("[7.0.0,)")
    // 3.2.0 depends on org.meteogroup.jbrotli:jbrotli:0.5.0 that is not available in central
    skip("3.2.0")
    assertInverse.set(true)
  }
}

// javalin 7 requires java 17
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  library("io.javalin:javalin:7.0.0")

  testImplementation(project(":instrumentation:javalin:testing"))

  testInstrumentation(project(":instrumentation:jetty:jetty-12.0:javaagent"))
  // Ensure no cross interference
  testInstrumentation(project(":instrumentation:javalin:javalin-5.0:javaagent"))
}
