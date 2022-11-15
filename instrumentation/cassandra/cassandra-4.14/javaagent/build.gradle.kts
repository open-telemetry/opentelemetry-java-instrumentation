plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.datastax.oss")
    module.set("java-driver-core")
    versions.set("[4.14,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:cassandra:cassandra-4.0:javaagent"))

  library("com.datastax.oss:java-driver-core:4.14.1")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation("io.projectreactor:reactor-core:3.4.21")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
