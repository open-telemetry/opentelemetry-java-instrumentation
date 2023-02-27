plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.datastax.oss")
    module.set("java-driver-core")
    versions.set("[4.0,4.4)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:cassandra:cassandra-4.4:library"))

  library("com.datastax.oss:java-driver-core:4.0.0")

  testImplementation(project(":instrumentation:cassandra:testing"))
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
