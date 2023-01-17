plugins {
  id("otel.javaagent-instrumentation")
}

// building against 2.3 and testing against 2.4 because JettyHandler is available since 2.4 only
muzzle {
  pass {
    group.set("com.sparkjava")
    module.set("spark-core")
    versions.set("[2.3,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.sparkjava:spark-core:2.3")

  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))

  testLibrary("com.sparkjava:spark-core:2.4")
}