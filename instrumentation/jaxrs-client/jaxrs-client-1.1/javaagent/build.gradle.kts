plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.sun.jersey")
    module.set("jersey-client")
    versions.set("[1.1,]")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.sun.jersey:jersey-client:1.1.4")

  testInstrumentation(project(":instrumentation:http-url-connection:javaagent"))
}
