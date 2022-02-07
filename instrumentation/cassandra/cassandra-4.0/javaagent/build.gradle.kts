plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.datastax.oss")
    module.set("java-driver-core")
    versions.set("[4.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.datastax.oss:java-driver-core:4.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
