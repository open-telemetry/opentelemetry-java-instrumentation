plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.aerospike")
    module.set("aerospike-client")
    versions.set("[4.4.9,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:aerospike-client:aerospike-client-7.1:library"))
  library("com.aerospike:aerospike-client:7.1.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
  testImplementation(project(":instrumentation:aerospike-client:aerospike-client-7.1:library"))
}

tasks {
  test {
    jvmArgs("-Djava.net.preferIPv4Stack=true")
    jvmArgs("-Dotel.instrumentation.aerospike.experimental-span-attributes=true")
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
