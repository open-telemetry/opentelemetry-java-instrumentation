plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.logging.log4j")
    module.set("log4j-core")
    versions.set("[2.7,2.16.0)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.logging.log4j:log4j-core:2.7")

  testImplementation(project(":instrumentation:log4j:log4j-2-common:testing"))

  latestDepTestLibrary("org.apache.logging.log4j:log4j-core:2.15.0")
}
