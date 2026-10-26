plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("com.sparkjava")
    module.set("spark-core")
    versions.set("[2.4,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.sparkjava:spark-core:2.4")

  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))
}
