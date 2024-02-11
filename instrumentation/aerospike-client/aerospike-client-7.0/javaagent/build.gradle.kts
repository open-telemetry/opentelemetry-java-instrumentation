plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.aerospike")
    module.set("aerospike-client")
    versions.set("[7.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.aerospike:aerospike-client:7.0.0")
  implementation("io.opentelemetry:opentelemetry-extension-incubator")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

tasks {
  test {
    jvmArgs("-Djava.net.preferIPv4Stack=true")
    jvmArgs("-Dotel.instrumentation.aerospike.experimental-span-attributes=true")
    jvmArgs("-Dotel.instrumentation.aerospike.experimental-metrics=true")
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
