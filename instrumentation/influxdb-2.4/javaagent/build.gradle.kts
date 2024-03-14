plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.influxdb")
    module.set("influxdb-java")
    versions.set("[2.4,)")
    assertInverse.set(true)
  }
}

dependencies {
  // from 2.14, it contains all methods need to instrument and can write all test cases
  library("org.influxdb:influxdb-java:2.14")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
